package com.rusuh.app.domain.repository

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.PaymentStatusResult
import com.rusuh.app.domain.model.PaymentSummary
import com.rusuh.app.domain.model.SnapTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Repository for payment operations.
 *
 * Payment summaries and status are read from the booking API.
 */
interface PaymentRepository {
    /**
     * Load order details, price breakdown, countdown, and local contact email.
     */
    suspend fun loadPayment(bookingId: String): DomainResult<PaymentSummary>

    /**
     * Start payment and return the Snap handoff URL.
     */
    suspend fun startSnap(bookingId: String): DomainResult<SnapTransaction>

    /**
     * Observe payment status for [bookingId], polling every 3-5 seconds with jitter.
     * Emits [PaymentStatusResult] on each poll; completes when the booking reaches a final status.
     * Throws on unrecoverable errors (network errors may be retried internally).
     */
    fun observePaymentStatus(bookingId: String): Flow<PaymentStatusResult>
}
