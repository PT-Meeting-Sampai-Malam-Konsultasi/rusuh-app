package com.rusuh.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /core/payments/{bookingId}` response: the payment summary shown while the hold is active.
 * Maps to [com.rusuh.app.domain.model.PaymentSummary].
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

/** One attendee line in the payment summary: category + masked NIK. */
@Serializable
data class AttendeeDto(
    @SerialName("categoryId") val categoryId: String = "",
    @SerialName("categoryName") val categoryName: String = "",
    @SerialName("fullName") val fullName: String = "",
    @SerialName("nikMasked") val nikMasked: String = "",
)

/** Price breakdown from the booking, used in both booking and payment summaries. */
@Serializable
data class PriceBreakdownDto(
    @SerialName("lines") val lines: List<PriceLineDto> = emptyList(),
    @SerialName("ticketSubtotal") val ticketSubtotal: MoneyDto = MoneyDto(),
    @SerialName("adminFee") val adminFee: MoneyDto = MoneyDto(),
    @SerialName("tax") val tax: MoneyDto? = null,
    @SerialName("total") val total: MoneyDto = MoneyDto(),
)

/** One line in the price breakdown: category × quantity. */
@Serializable
data class PriceLineDto(
    @SerialName("categoryId") val categoryId: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("quantity") val quantity: Int = 0,
    @SerialName("unitPrice") val unitPrice: MoneyDto = MoneyDto(),
    @SerialName("lineTotal") val lineTotal: MoneyDto = MoneyDto(),
)

// Note: MoneyDto is defined in EventDtos.kt and reused here.

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
