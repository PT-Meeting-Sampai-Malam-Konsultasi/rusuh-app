package com.rusuh.app.data.remote

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.idempotencyKey
import com.rusuh.app.data.network.jsonBody
import com.rusuh.app.data.remote.dto.PaymentStatusDto
import com.rusuh.app.data.remote.dto.PaymentViewDto
import com.rusuh.app.data.remote.dto.SnapTransactionDto
import com.rusuh.app.data.remote.dto.StartSnapRequestDto

/**
 * Payment endpoints on the core API host (`/core/payments/...`).
 *
 * The payment routes are not yet deployed on the live staging backend (404), so this service is
 * currently used only by stub/mock mode and unit tests. The routes follow the Apidog contract.
 */
class PaymentApiService(
    private val api: ApiClient,
) {
    /**
     * `GET /core/payments/{bookingId}`: payment page data (order summary, breakdown, countdown, Snap client key).
     * Requires a Bearer token.
     */
    suspend fun getPayment(bookingId: String): ApiResult<PaymentViewDto> =
        api.get("$EP_PAYMENTS/${pathSegment(bookingId)}")

    /**
     * `POST /core/payments/{bookingId}/snap`: create (or return) the Midtrans Snap transaction for the booking.
     * Requires a Bearer token and an Idempotency-Key per attempt. Returns 201 on success, 409 BOOKING_NOT_HELD,
     * or 503 PAYMENT_PROVIDER_BUSY.
     *
     * @param bookingId the booking being paid
     * @param idempotencyKey unique per payment attempt (reuse on retry, new key for a fresh attempt)
     * @param method optional pre-selected payment method; null lets the user pick on the Snap page
     */
    suspend fun startSnap(
        bookingId: String,
        idempotencyKey: String,
        method: String? = null,
    ): ApiResult<SnapTransactionDto> =
        api.post("$EP_PAYMENTS/${pathSegment(bookingId)}/snap") {
            idempotencyKey(idempotencyKey)
            if (method != null) {
                jsonBody(StartSnapRequestDto(method = method))
            }
        }

    /**
     * `GET /core/payments/{bookingId}/status`: poll payment status (every 3-5 s, with jitter).
     * Requires a Bearer token. Returns the booking and payment status; when final, includes `returnUrl`.
     */
    suspend fun getPaymentStatus(bookingId: String): ApiResult<PaymentStatusDto> =
        api.get("$EP_PAYMENTS/${pathSegment(bookingId)}/status")

    private companion object {
        const val EP_PAYMENTS = "/core/payments"
    }
}
