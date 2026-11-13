package com.rusuh.app.domain.model

import kotlinx.datetime.Instant

/**
 * Domain model for the payment summary shown on the handoff screen.
 * Mapped from a booking record and the locally stored account email.
 */
data class PaymentSummary(
    val bookingId: String,
    val orderCode: String,
    val eventName: String?,
    val eventStartsAt: Instant?,
    val venueShort: String?,
    val timezone: String?,
    val attendees: List<AttendeeInfo>,
    val customerEmail: String?,
    val price: PriceBreakdown,
    val expiresAt: Instant,
    val serverNow: Instant,
    val bookingStatus: BookingStatus = BookingStatus.SELECTION,
    val refundRequired: Boolean = false,
)

/** One attendee line in the payment summary: category + masked NIK. */
data class AttendeeInfo(
    val categoryId: String,
    val categoryName: String,
    val fullName: String,
    val nikMasked: String,
)

/** Price breakdown: ticket lines, subtotal, admin fee, optional tax, total. */
data class PriceBreakdown(
    val lines: List<PriceLine>,
    val ticketSubtotal: Long,
    val adminFee: Long,
    val tax: Long?,
    val total: Long,
)

/** One line in the price breakdown: category × quantity. */
data class PriceLine(
    val categoryId: String,
    val name: String,
    val quantity: Int,
    val unitPrice: Long,
    val lineTotal: Long,
)

/**
 * Midtrans Snap transaction details.
 * Mapped from the booking payment-entry response.
 */
data class SnapTransaction(
    val bookingId: String,
    val snapToken: String,
    val redirectUrl: String?,
    val paymentExpiresAt: Instant,
)

/**
 * Result of checking payment status through the booking API.
 */
data class PaymentStatusResult(
    val bookingStatus: BookingStatus,
    val paymentStatus: PaymentStatus,
    val returnUrl: String?,
    val serverNow: Instant,
)

// BookingStatus is defined in DetailModels.kt with values: SELECTION, PAYMENT_PENDING, HELD, PAID,
// EXPIRED, CANCELLED, REFUND_PENDING, REFUNDED. It includes helper properties isFinal and isPaid.

/**
 * Payment status as defined in the API contract.
 */
enum class PaymentStatus {
    NONE,
    PENDING,
    SETTLED,
    DENIED,
    EXPIRED,
    CANCELLED,
    FAILED,
    PAID_AFTER_EXPIRY,
    ;

    companion object {
        fun fromWire(value: String?): PaymentStatus =
            value?.let { v -> entries.firstOrNull { it.name.equals(v, ignoreCase = true) } } ?: NONE
    }

    /** True when the payment is in a final state. */
    val isFinal: Boolean get() = this in setOf(SETTLED, DENIED, EXPIRED, CANCELLED, FAILED, PAID_AFTER_EXPIRY)

    /** True when payment succeeded (settled, or arrived late). */
    val isSuccess: Boolean get() = this in setOf(SETTLED, PAID_AFTER_EXPIRY)
}
