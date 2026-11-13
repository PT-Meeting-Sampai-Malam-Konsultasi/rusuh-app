package com.rusuh.app.data.repository

import com.rusuh.app.data.remote.PaymentApiService
import com.rusuh.app.data.remote.mapper.toDomain
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.PaymentMethod
import com.rusuh.app.domain.model.PaymentStatusResult
import com.rusuh.app.domain.model.PaymentSummary
import com.rusuh.app.domain.model.SnapTransaction
import com.rusuh.app.domain.repository.PaymentRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * [PaymentRepositoryImpl] that calls the payment API endpoints.
 *
 * Note: The payment routes are not yet deployed on the live staging backend (404), so this
 * implementation works only against mock/stub servers for now.
 *
 * @param api the payment API service
 * @param pollIntervalMs base polling interval in milliseconds (default: 3 seconds)
 * @param jitterMs maximum jitter to add to the polling interval (default: 2 seconds)
 * @param random random source for jitter (injectable for testing)
 */
class PaymentRepositoryImpl(
    private val api: PaymentApiService,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val jitterMs: Long = DEFAULT_JITTER_MS,
    private val random: Random = Random.Default,
) : PaymentRepository {
    override suspend fun loadPayment(bookingId: String): DomainResult<PaymentSummary> =
        api.getPayment(bookingId).toDomainResult { it.toDomain() }

    override suspend fun startSnap(
        bookingId: String,
        idempotencyKey: String,
        method: PaymentMethod?,
    ): DomainResult<SnapTransaction> =
        api.startSnap(
            bookingId = bookingId,
            idempotencyKey = idempotencyKey,
            method = method?.name?.lowercase(),
        ).toDomainResult { it.toDomain() }

    override fun observePaymentStatus(bookingId: String): Flow<PaymentStatusResult> =
        flow {
            var consecutiveFailures = 0
            var shouldContinue = true

            while (shouldContinue) {
                val pollResult = pollOnce(bookingId, consecutiveFailures)

                when (pollResult) {
                    is PollResult.Success -> {
                        consecutiveFailures = 0
                        emit(pollResult.data)
                        shouldContinue = !pollResult.data.bookingStatus.isFinal
                    }
                    is PollResult.RetryableFailure -> {
                        consecutiveFailures = pollResult.newFailureCount
                        delay(pollIntervalMs * 2)
                    }
                    is PollResult.FatalFailure -> throw pollResult.exception
                }

                if (shouldContinue) {
                    val jitter = if (jitterMs > 0) random.nextLong(jitterMs) else 0
                    delay(pollIntervalMs + jitter)
                }
            }
        }

    private suspend fun pollOnce(
        bookingId: String,
        currentFailures: Int,
    ): PollResult {
        val result = api.getPaymentStatus(bookingId).toDomainResult { it.toDomain() }

        return when (result) {
            is DomainResult.Success -> PollResult.Success(result.data)
            is DomainResult.NetworkError -> {
                val newFailures = currentFailures + 1
                if (newFailures >= MAX_CONSECUTIVE_FAILURES) {
                    PollResult.FatalFailure(
                        PaymentPollingException("Network error after $MAX_CONSECUTIVE_FAILURES retries"),
                    )
                } else {
                    PollResult.RetryableFailure(newFailures)
                }
            }
            is DomainResult.Error ->
                PollResult.FatalFailure(
                    PaymentPollingException("Payment status check failed: ${result.errorCode.name} (${result.code})"),
                )
            is DomainResult.Empty, is DomainResult.TechnicalError ->
                PollResult.FatalFailure(
                    PaymentPollingException("Unexpected response when checking payment status"),
                )
        }
    }

    private sealed interface PollResult {
        data class Success(val data: PaymentStatusResult) : PollResult

        data class RetryableFailure(val newFailureCount: Int) : PollResult

        data class FatalFailure(val exception: PaymentPollingException) : PollResult
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 3_000L
        const val DEFAULT_JITTER_MS = 2_000L
        const val MAX_CONSECUTIVE_FAILURES = 5
    }
}

/** Exception thrown when payment status polling fails unrecoverably. */
class PaymentPollingException(message: String) : Exception(message)
