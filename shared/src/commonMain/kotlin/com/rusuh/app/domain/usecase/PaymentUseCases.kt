package com.rusuh.app.domain.usecase

import com.rusuh.app.data.network.newIdempotencyKey
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.PaymentMethod
import com.rusuh.app.domain.model.PaymentStatusResult
import com.rusuh.app.domain.model.PaymentSummary
import com.rusuh.app.domain.model.SnapTransaction
import com.rusuh.app.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow

/**
 * Load the payment summary for a booking: order details, price breakdown, countdown, Snap client key.
 * Called when entering the payment screen.
 */
class LoadPaymentUseCase(
    private val repository: PaymentRepository,
) {
    suspend operator fun invoke(bookingId: String): DomainResult<PaymentSummary> = repository.loadPayment(bookingId)
}

/**
 * Start a Midtrans Snap payment attempt.
 *
 * Generates a unique idempotency key per call. For retries of the same user attempt, use
 * [invokeWithKey] with the same key; for a fresh attempt (e.g., after closing and reopening Snap),
 * call [invoke] to get a new key.
 */
class StartSnapPaymentUseCase(
    private val repository: PaymentRepository,
) {
    /**
     * Start a new payment attempt with a fresh idempotency key.
     * @return the Snap transaction and the idempotency key used (save it for retries)
     */
    suspend fun invoke(
        bookingId: String,
        method: PaymentMethod? = null,
    ): StartSnapResult {
        val key = newIdempotencyKey()
        val result = repository.startSnap(bookingId, key, method)
        return StartSnapResult(result, key)
    }

    /**
     * Retry a payment attempt with the same idempotency key (server deduplicates repeats).
     */
    suspend fun invokeWithKey(
        bookingId: String,
        idempotencyKey: String,
        method: PaymentMethod? = null,
    ): DomainResult<SnapTransaction> = repository.startSnap(bookingId, idempotencyKey, method)
}

/** Result of [StartSnapPaymentUseCase.invoke], including the idempotency key for retries. */
data class StartSnapResult(
    val result: DomainResult<SnapTransaction>,
    val idempotencyKey: String,
)

/**
 * Observe payment status for a booking, polling every 3-5 seconds with jitter.
 *
 * The flow emits [PaymentStatusResult] on each poll and completes when the booking reaches a
 * final status (PAID, EXPIRED, CANCELLED). Callers should collect this flow after the user
 * returns from the Snap page.
 *
 * The flow throws on unrecoverable errors; transient network errors are retried internally.
 */
class ObservePaymentStatusUseCase(
    private val repository: PaymentRepository,
) {
    operator fun invoke(bookingId: String): Flow<PaymentStatusResult> = repository.observePaymentStatus(bookingId)
}
