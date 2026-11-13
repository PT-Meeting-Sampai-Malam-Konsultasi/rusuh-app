package com.rusuh.app.feature.payment

import com.rusuh.app.data.repository.StubPaymentRepository
import com.rusuh.app.data.stub.StubClock
import com.rusuh.app.domain.model.BookingStatus
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.PaymentStatus
import com.rusuh.app.domain.model.PaymentStatusResult
import com.rusuh.app.domain.model.PaymentSummary
import com.rusuh.app.domain.repository.PaymentRepository
import com.rusuh.app.domain.usecase.LoadPaymentUseCase
import com.rusuh.app.domain.usecase.ObservePaymentStatusUseCase
import com.rusuh.app.domain.usecase.StartSnapPaymentUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelResumeCoverageTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeRepository : PaymentRepository {
        private val delegate = StubPaymentRepository(StubClock())
        var status = BookingStatus.PAYMENT_PENDING
        var paymentStatus = PaymentStatus.PENDING
        var loadCount = 0
        var statusCount = 0
        var resumeResult: DomainResult<PaymentSummary>? = null
        var expireOnResume = false
        var throwStatus = false
        private var summary: PaymentSummary? = null

        override suspend fun loadPayment(bookingId: String): DomainResult<PaymentSummary> {
            loadCount++
            return if (loadCount > 1) {
                resumeResult ?: run {
                    val current = requireNotNull(summary)
                    val refreshed = if (expireOnResume) current.copy(expiresAt = current.serverNow) else current
                    summary = refreshed
                    DomainResult.Success(refreshed)
                }
            } else {
                val loaded = (delegate.loadPayment(bookingId) as DomainResult.Success).data
                loaded.copy(
                    bookingStatus = BookingStatus.PAYMENT_PENDING,
                ).also { summary = it }.let { DomainResult.Success(it) }
            }
        }

        override suspend fun startSnap(bookingId: String) = delegate.startSnap(bookingId)

        override fun observePaymentStatus(bookingId: String): Flow<PaymentStatusResult> {
            statusCount++
            if (throwStatus) return flow { throw IllegalStateException("synthetic failure") }
            val now = summary?.serverNow ?: Instant.DISTANT_PAST
            return flowOf(PaymentStatusResult(status, paymentStatus, null, now))
        }
    }

    private fun viewModel(
        repository: FakeRepository,
        clock: () -> Long = { 0L },
    ) = PaymentViewModel(
        "bk_test_123",
        LoadPaymentUseCase(repository),
        StartSnapPaymentUseCase(repository),
        ObservePaymentStatusUseCase(repository),
        monotonicMillis = clock,
    )

    @Test
    fun loadWithOpenSnapChecksStatus() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val viewModel = viewModel(repository)
            viewModel.dispatch(PaymentIntent.Load)
            testScheduler.advanceUntilIdle()

            assertEquals(1, repository.statusCount)
            assertIs<PaymentUiState.Ready>(viewModel.state.value.ui)
        }

    @Test
    fun statusOutcomesUpdatePaymentState() =
        runTest(dispatcher) {
            val scenarios =
                listOf(
                    Triple(BookingStatus.PAID, PaymentStatus.SETTLED, PaymentUiState.Success::class),
                    Triple(BookingStatus.EXPIRED, PaymentStatus.EXPIRED, PaymentUiState.Expired::class),
                    Triple(BookingStatus.CANCELLED, PaymentStatus.CANCELLED, PaymentUiState.Expired::class),
                    Triple(BookingStatus.EXPIRED, PaymentStatus.PAID_AFTER_EXPIRY, PaymentUiState.Expired::class),
                    Triple(BookingStatus.PAYMENT_PENDING, PaymentStatus.PENDING, PaymentUiState.Ready::class),
                )

            scenarios.forEach { (bookingStatus, paymentStatus, expected) ->
                val repository =
                    FakeRepository().apply {
                        status = bookingStatus
                        this.paymentStatus = paymentStatus
                    }
                val viewModel = viewModel(repository)
                viewModel.dispatch(PaymentIntent.Load)
                testScheduler.advanceUntilIdle()

                assertEquals(expected, viewModel.state.value.ui::class)
            }
        }

    @Test
    fun statusFailureShowsGenericError() =
        runTest(dispatcher) {
            val repository = FakeRepository().apply { throwStatus = true }
            val viewModel = viewModel(repository)
            viewModel.dispatch(PaymentIntent.Load)
            testScheduler.advanceUntilIdle()

            assertEquals(PaymentUiState.Error("Something went wrong"), viewModel.state.value.ui)
        }

    @Test
    fun resumeLoadErrorKeepsRunningDeadline() =
        runTest(dispatcher) {
            val failures = listOf(DomainResult.NetworkError, DomainResult.Empty, DomainResult.TechnicalError)
            failures.forEach { failure ->
                val repository = FakeRepository().apply { resumeResult = failure }
                val viewModel = viewModel(repository)
                viewModel.dispatch(PaymentIntent.Load)
                testScheduler.advanceUntilIdle()
                viewModel.dispatch(PaymentIntent.Resumed)
                testScheduler.advanceUntilIdle()

                assertIs<PaymentUiState.Ready>(viewModel.state.value.ui)
                assertEquals(600, viewModel.state.value.remainingSeconds)
                assertEquals(1, repository.statusCount)
            }
        }

    @Test
    fun resumeLoadErrorExpiresAtDeadline() =
        runTest(dispatcher) {
            var now = 0L
            val repository = FakeRepository().apply { resumeResult = DomainResult.Error(code = 503) }
            val viewModel = viewModel(repository) { now }
            viewModel.dispatch(PaymentIntent.Load)
            testScheduler.advanceUntilIdle()
            now = 600_000L
            viewModel.dispatch(PaymentIntent.Resumed)
            testScheduler.advanceUntilIdle()

            assertIs<PaymentUiState.Expired>(viewModel.state.value.ui)
            assertEquals(1, repository.statusCount)
        }

    @Test
    fun statusExpiresWhenDeadlinePassed() =
        runTest(dispatcher) {
            var now = 0L
            val repository = FakeRepository().apply { expireOnResume = true }
            val viewModel = viewModel(repository) { now }
            viewModel.dispatch(PaymentIntent.Load)
            testScheduler.advanceUntilIdle()
            now = 600_000L
            viewModel.dispatch(PaymentIntent.Resumed)
            testScheduler.advanceUntilIdle()

            assertIs<PaymentUiState.Expired>(viewModel.state.value.ui)
        }
}
