@file:Suppress(
    "WildcardImport",
    "MaxLineLength",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package com.rusuh.app.feature.payment

import com.rusuh.app.domain.model.*
import com.rusuh.app.domain.repository.PaymentRepository
import com.rusuh.app.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlinx.datetime.Instant
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentResultViewModelCoverageTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Repo(var status: PaymentStatusResult, var summary: DomainResult<PaymentSummary>) : PaymentRepository {
        override suspend fun loadPayment(bookingId: String) = summary

        override suspend fun startSnap(bookingId: String) = DomainResult.TechnicalError

        override fun observePaymentStatus(bookingId: String) = flowOf(status)
    }

    private val payment =
        PaymentSummary(
            "bk_test", "RSH-TEST", "Event", null, null, null,
            listOf(
                AttendeeInfo("cat", "General", "Guest", "********0001"),
            ),
            "test@example.com",
            PriceBreakdown(
                emptyList(),
                1,
                0,
                null,
                1,
            ),
            Instant.parse("2026-11-14T10:10:00Z"), Instant.parse("2026-11-14T10:00:00Z"),
        )

    private fun status(
        booking: BookingStatus,
        payment: PaymentStatus = PaymentStatus.PENDING,
    ) = PaymentStatusResult(
        booking,
        payment,
        null,
        Instant.parse("2026-11-14T10:00:00Z"),
    )

    private fun TestScope.run(repo: Repo): PaymentResultViewModel {
        val vm = PaymentResultViewModel("bk_test", LoadPaymentUseCase(repo), ObservePaymentStatusUseCase(repo))
        vm.dispatch(PaymentResultIntent.Load)
        testScheduler.advanceUntilIdle()
        return vm
    }

    @Test fun mapsPaidExpiredCancelledAndPendingBookings() =
        runTest(dispatcher) {
            assertIs<PaymentResultUiState.Success>(
                run(Repo(status(BookingStatus.PAID), DomainResult.Success(payment))).state.value.ui,
            )
            assertIs<PaymentResultUiState.Expired>(
                run(
                    Repo(status(BookingStatus.EXPIRED, PaymentStatus.PAID_AFTER_EXPIRY), DomainResult.Success(payment)),
                ).state.value.ui,
            )
            assertTrue(
                assertIs<PaymentResultUiState.Expired>(
                    run(Repo(status(BookingStatus.CANCELLED), DomainResult.Success(payment))).state.value.ui,
                ).refundRequired.not(),
            )
            val pending = run(Repo(status(BookingStatus.PAYMENT_PENDING), DomainResult.Success(payment)))
            assertIs<PaymentResultUiState.Loading>(pending.state.value.ui)
        }

    @Test fun navigationEffectsMap() =
        runTest(dispatcher) {
            val vm =
                PaymentResultViewModel(
                    "bk_test",
                    LoadPaymentUseCase(Repo(status(BookingStatus.PAID), DomainResult.Success(payment))),
                    ObservePaymentStatusUseCase(Repo(status(BookingStatus.PAID), DomainResult.Success(payment))),
                )
            vm.dispatch(PaymentResultIntent.GoToTickets)
            vm.dispatch(PaymentResultIntent.GoHome)
            testScheduler.advanceUntilIdle()
            assertEquals(PaymentResultEffect.NavigateToTickets, vm.effect.first())
            assertEquals(PaymentResultEffect.NavigateToHome, vm.effect.first())
        }
}
