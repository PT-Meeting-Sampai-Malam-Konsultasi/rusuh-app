@file:Suppress(
    "WildcardImport",
    "MaxLineLength",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package com.rusuh.app.feature.payment

import com.rusuh.app.domain.model.*
import com.rusuh.app.domain.repository.DetailRepository
import com.rusuh.app.domain.repository.PaymentRepository
import com.rusuh.app.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.datetime.Instant
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelCoverageTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Repo(
        var load: DomainResult<PaymentSummary>,
        var snap: DomainResult<SnapTransaction> =
            DomainResult.Success(
                tx,
            ),
    ) : PaymentRepository {
        override suspend fun loadPayment(bookingId: String) = load

        override suspend fun startSnap(bookingId: String) = snap

        override fun observePaymentStatus(bookingId: String) = emptyFlow<PaymentStatusResult>()
    }

    private fun summary(expiry: String = "2026-11-14T10:10:00Z") =
        PaymentSummary(
            "bk_test", "RSH-TEST", "Event", null, "Hall", "UTC", emptyList(), "test@example.com",
            PriceBreakdown(
                emptyList(),
                100,
                2,
                null,
                102,
            ),
            Instant.parse(expiry), Instant.parse("2026-11-14T10:00:00Z"),
        )

    private fun vm(
        repo: Repo,
        cancelBooking: CancelBookingUseCase? = null,
        now: () -> Long = {
            0L
        },
    ) = PaymentViewModel(
        "bk_test",
        LoadPaymentUseCase(repo),
        StartSnapPaymentUseCase(repo),
        ObservePaymentStatusUseCase(repo),
        now,
        cancelBooking = cancelBooking,
    )

    private class BookingRepo(var cancelResult: DomainResult<Booking>) : DetailRepository {
        override suspend fun getDetail(eventId: String): DetailOffering = error("unused")

        override suspend fun validateAttendee(
            eventId: String,
            fieldIndex: Int,
            fullName: String,
            nik: String,
        ) = AttendeeValidation.NetworkError

        @Suppress("DEPRECATION")
        override suspend fun isNikAccepted(nik: String) = false

        override suspend fun createBooking(
            request: BookingRequest,
            idempotencyKey: String,
        ): BookingResult = BookingResult.NetworkError

        override suspend fun getBooking(bookingId: String): DomainResult<Booking> = DomainResult.Empty

        override suspend fun cancelBooking(bookingId: String) = cancelResult

        override suspend fun getActiveBookings(): DomainResult<List<Booking>> = DomainResult.Empty

        @Suppress("DEPRECATION")
        override suspend fun createBooking(request: BookingRequest): Booking = Booking(bookingId = "bk_test")
    }

    private fun TestScope.send(
        vm: PaymentViewModel,
        vararg intents: PaymentIntent,
    ) {
        intents.forEach(vm::dispatch)
        testScheduler.advanceUntilIdle()
    }

    @Test fun loadUsesServerDeadlineAndTickExpires() =
        runTest(dispatcher) {
            val repo = Repo(DomainResult.Success(summary()))
            var now = 0L
            val viewModel = vm(repo) { now }
            send(viewModel, PaymentIntent.Load)
            assertEquals(600, viewModel.state.value.remainingSeconds)
            assertIs<PaymentUiState.Ready>(viewModel.state.value.ui)
            now = 600_000L
            send(viewModel, PaymentIntent.Tick)
            assertIs<PaymentUiState.Expired>(viewModel.state.value.ui)
        }

    @Test fun loadFailuresAndExpiredDeadlineMapToStates() =
        runTest(dispatcher) {
            val notHeld = vm(Repo(DomainResult.Error(code = 409, errorCode = ErrorCode.BOOKING_NOT_HELD)))
            send(notHeld, PaymentIntent.Load)
            assertIs<PaymentUiState.Error>(notHeld.state.value.ui)
            val expired = vm(Repo(DomainResult.Success(summary("2026-11-14T09:59:00Z"))))
            send(expired, PaymentIntent.Load)
            assertIs<PaymentUiState.Expired>(expired.state.value.ui)
            val offline = vm(Repo(DomainResult.NetworkError))
            send(offline, PaymentIntent.Load)
            assertEquals("Could not reach the server", assertIs<PaymentUiState.Error>(offline.state.value.ui).message)
        }

    @Test fun snapSuccessAndErrorsMap() =
        runTest(dispatcher) {
            val repo = Repo(DomainResult.Success(summary()))
            val viewModel = vm(repo)
            send(viewModel, PaymentIntent.Load, PaymentIntent.OpenSnap)
            assertEquals(PaymentEffect.LaunchSnap(requireNotNull(tx.redirectUrl)), viewModel.effect.first())
            repo.snap = DomainResult.Error(code = 503, errorCode = ErrorCode.PAYMENT_PROVIDER_BUSY, retryAfterSeconds = 12)
            send(viewModel, PaymentIntent.RetrySnap)
            assertEquals(12, assertIs<PaymentUiState.Ready>(viewModel.state.value.ui).busyError?.retryAfterSeconds)
            repo.snap = DomainResult.Error(code = 409, errorCode = ErrorCode.BOOKING_NOT_HELD)
            send(viewModel, PaymentIntent.RetrySnap)
            assertIs<PaymentUiState.Expired>(viewModel.state.value.ui)
        }

    @Test fun missingSnapRedirectShowsGenericError() =
        runTest(dispatcher) {
            val repo =
                Repo(DomainResult.Success(summary())).apply {
                    snap = DomainResult.Success(tx.copy(redirectUrl = null))
                }
            val viewModel = vm(repo)
            send(viewModel, PaymentIntent.Load, PaymentIntent.OpenSnap)
            assertEquals("Something went wrong", assertIs<PaymentUiState.Error>(viewModel.state.value.ui).message)
        }

    @Test fun cancellationHandlesSuccessAndNotCancellable() =
        runTest(dispatcher) {
            val bookingRepo =
                BookingRepo(DomainResult.Success(Booking(bookingId = "bk_test", status = BookingStatus.CANCELLED)))
            val viewModel = vm(Repo(DomainResult.Success(summary())), cancelBooking = CancelBookingUseCase(bookingRepo))
            send(viewModel, PaymentIntent.Load, PaymentIntent.CancelBooking)
            assertIs<PaymentUiState.Expired>(viewModel.state.value.ui)

            bookingRepo.cancelResult = DomainResult.Error(code = 409, errorCode = ErrorCode.NOT_CANCELLABLE)
            val blocked = vm(Repo(DomainResult.Success(summary())), cancelBooking = CancelBookingUseCase(bookingRepo))
            send(blocked, PaymentIntent.Load, PaymentIntent.CancelBooking)
            assertEquals(CancelError.NOT_CANCELLABLE, blocked.state.value.cancelError)
            assertIs<PaymentUiState.Ready>(blocked.state.value.ui)
        }

    @Test fun backAndExpiredActionsEmitNavigationEffects() =
        runTest(dispatcher) {
            val viewModel = vm(Repo(DomainResult.Success(summary("2026-11-14T09:59:00Z"))))
            send(viewModel, PaymentIntent.Load, PaymentIntent.Back, PaymentIntent.Back)
            assertEquals(PaymentEffect.NavigateBack, viewModel.effect.first())
            send(viewModel, PaymentIntent.QueueAgain, PaymentIntent.GoHome, PaymentIntent.GoToTickets)
            assertEquals(PaymentEffect.NavigateToHome, viewModel.effect.first())
            assertEquals(PaymentEffect.NavigateToHome, viewModel.effect.first())
            assertEquals(PaymentEffect.NavigateToTickets, viewModel.effect.first())
        }

    companion object {
        private val tx =
            SnapTransaction(
                "bk_test",
                "token",
                "https://pay.example.test/snap",
                Instant.parse("2026-11-14T10:10:00Z"),
            )
    }
}
