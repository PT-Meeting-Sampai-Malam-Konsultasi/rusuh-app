package com.rusuh.app.domain.repository

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.PaymentMethod
import com.rusuh.app.domain.model.PaymentStatusResult
import com.rusuh.app.domain.model.PaymentSummary
import com.rusuh.app.domain.model.SnapTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Repository for payment operations.
 *
 * The payment routes are not yet deployed on the live staging backend (404), so implementations
 * are either [com.rusuh.app.data.repository.StubPaymentRepository] for stub mode or
 * [com.rusuh.app.data.repository.PaymentRepositoryImpl] for real/mock mode.
 */
interface PaymentRepository {
    /**
     * Load the payment summary for [bookingId]: order details, price breakdown, countdown, Snap client key.
     */
    suspend fun loadPayment(bookingId: String): DomainResult<PaymentSummary>

    /**
     * Start the Midtrans Snap payment for [bookingId].
     *
     * @param idempotencyKey unique per payment attempt (reuse on retry, new key for a fresh attempt)
     * @param method optional pre-selected payment method; null lets the user pick on the Snap page
     * @return the Snap transaction with the redirect URL to open in a system tab
     */
    suspend fun startSnap(
        bookingId: String,
        idempotencyKey: String,
        method: PaymentMethod? = null,
    ): DomainResult<SnapTransaction>

    /**
     * Observe payment status for [bookingId], polling every 3-5 seconds with jitter.
     * Emits [PaymentStatusResult] on each poll; completes when the booking reaches a final status.
     * Throws on unrecoverable errors (network errors may be retried internally).
     */
    fun observePaymentStatus(bookingId: String): Flow<PaymentStatusResult>
}
