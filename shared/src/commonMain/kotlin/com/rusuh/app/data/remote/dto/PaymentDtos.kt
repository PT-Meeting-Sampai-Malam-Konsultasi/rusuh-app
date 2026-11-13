package com.rusuh.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /core/payments/{bookingId}` response: the payment summary shown while the hold is active.
 * Maps to [com.rusuh.app.domain.model.PaymentSummary].
 *
 * Reuses [AttendeeDto] and [PriceBreakdownDto] from BookingDtos.kt.
 */
@Serializable
data class PaymentViewDto(
    @SerialName("bookingId") val bookingId: String = "",
    @SerialName("orderCode") val orderCode: String = "",
    @SerialName("eventName") val eventName: String? = null,
    @SerialName("eventStartsAt") val eventStartsAt: String? = null,
    @SerialName("venueShort") val venueShort: String? = null,
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("attendees") val attendees: List<AttendeeDto> = emptyList(),
    @SerialName("customerEmail") val customerEmail: String? = null,
    @SerialName("price") val price: PriceBreakdownDto = PriceBreakdownDto(),
    @SerialName("currentSnap") val currentSnap: SnapTransactionDto? = null,
    @SerialName("expiresAt") val expiresAt: String = "",
    @SerialName("serverNow") val serverNow: String = "",
    @SerialName("csrfToken") val csrfToken: String = "",
    @SerialName("snapClientKey") val snapClientKey: String = "",
)

// Note: AttendeeDto, PriceBreakdownDto, PriceLineDto are defined in BookingDtos.kt
// Note: MoneyDto is defined in EventDtos.kt

/**
 * `POST /core/payments/{bookingId}/snap` response: the Midtrans Snap transaction details.
 * Maps to [com.rusuh.app.domain.model.SnapTransaction].
 */
@Serializable
data class SnapTransactionDto(
    @SerialName("attemptId") val attemptId: Int = 0,
    @SerialName("orderId") val orderId: String = "",
    @SerialName("method") val method: String? = null,
    @SerialName("snapToken") val snapToken: String = "",
    @SerialName("redirectUrl") val redirectUrl: String = "",
    @SerialName("gatewayExpiresAt") val gatewayExpiresAt: String = "",
)

/**
 * `GET /core/payments/{bookingId}/status` response: poll for payment status.
 * Maps to [com.rusuh.app.domain.model.PaymentStatusResult].
 */
@Serializable
data class PaymentStatusDto(
    @SerialName("bookingStatus") val bookingStatus: String = "",
    @SerialName("paymentStatus") val paymentStatus: String? = null,
    @SerialName("returnUrl") val returnUrl: String? = null,
    @SerialName("serverNow") val serverNow: String = "",
)

/**
 * `POST /core/payments/{bookingId}/snap` request body (optional method pre-selection).
 */
@Serializable
data class StartSnapRequestDto(
    @SerialName("method") val method: String? = null,
)
