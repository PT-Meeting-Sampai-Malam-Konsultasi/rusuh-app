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
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelResumeTest {
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
        var expireOnSecondLoad = false
        private var latest: PaymentSummary? = null

        override suspend fun loadPayment(bookingId: String): DomainResult<PaymentSummary> {
            loadCount++
            val result = delegate.loadPayment(bookingId)
            if (result is DomainResult.Success) {
                val summary = result.data
                val changed =
                    if (expireOnSecondLoad && loadCount > 1) {
                        summary.copy(expiresAt = summary.serverNow - 1.seconds)
                    } else {
                        summary
                    }
                latest = changed
                return DomainResult.Success(changed)
            }
            return result
        }

        override suspend fun startSnap(
            bookingId: String,
            idempotencyKey: String,
            method: com.rusuh.app.domain.model.PaymentMethod?,
        ) = delegate.startSnap(bookingId, idempotencyKey, method)

        override fun observePaymentStatus(bookingId: String): Flow<PaymentStatusResult> {
            statusCount++
            val now = latest?.serverNow ?: Instant.DISTANT_PAST
            return flowOf(PaymentStatusResult(status, paymentStatus, null, now))
        }
    }

    private fun viewModel(repository: FakeRepository) =
        PaymentViewModel(
            "bk_test_123",
            LoadPaymentUseCase(repository),
            StartSnapPaymentUseCase(repository),
            ObservePaymentStatusUseCase(repository),
            monotonicMillis = { 0L },
        )

    @Test
    fun resumeRefreshesTimeAndChecksStatusOnce() =
        runTest(dispatcher) {
            val repository =
                FakeRepository().apply {
                    status = BookingStatus.PAID
                    paymentStatus = PaymentStatus.SETTLED
                }
            val viewModel = viewModel(repository)
            viewModel.dispatch(PaymentIntent.Load)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(PaymentIntent.OpenSnap)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(PaymentIntent.Resumed)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(PaymentIntent.Resumed)
            testScheduler.advanceUntilIdle()

            assertIs<PaymentUiState.Success>(viewModel.state.value.ui)
            assertEquals(1, repository.statusCount)
            assertEquals(2, repository.loadCount)
        }

    @Test
    fun resumeExpiresFromRefreshedServerDeadline() =
        runTest(dispatcher) {
            val repository = FakeRepository().apply { expireOnSecondLoad = true }
            val viewModel = viewModel(repository)
            viewModel.dispatch(PaymentIntent.Load)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(PaymentIntent.OpenSnap)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(PaymentIntent.Resumed)
            testScheduler.advanceUntilIdle()

            assertIs<PaymentUiState.Expired>(viewModel.state.value.ui)
            assertEquals(1, repository.statusCount)
        }

    @Test
    fun paidStatusWinsWhenDeadlinePassed() =
        runTest(dispatcher) {
            val repository =
                FakeRepository().apply {
                    expireOnSecondLoad = true
                    status = BookingStatus.PAID
                    paymentStatus = PaymentStatus.SETTLED
                }
            val viewModel = viewModel(repository)
            viewModel.dispatch(PaymentIntent.Load)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(PaymentIntent.OpenSnap)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(PaymentIntent.Resumed)
            testScheduler.advanceUntilIdle()

            assertIs<PaymentUiState.Success>(viewModel.state.value.ui)
            assertEquals(1, repository.statusCount)
        }

    @Test
    fun closingSnapRefreshesBookingAndShowsPaidResult() =
        runTest(dispatcher) {
            val repository =
                FakeRepository().apply {
                    status = BookingStatus.PAID
                    paymentStatus = PaymentStatus.SETTLED
                }
            val viewModel = viewModel(repository)
            viewModel.dispatch(PaymentIntent.Load)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(PaymentIntent.OpenSnap)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(PaymentIntent.SnapClosed)
            testScheduler.advanceUntilIdle()

            assertIs<PaymentUiState.Success>(viewModel.state.value.ui)
            assertEquals(2, repository.loadCount)
        }
}
