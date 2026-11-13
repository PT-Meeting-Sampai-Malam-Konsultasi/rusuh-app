package com.rusuh.app.domain.model

import kotlinx.datetime.Instant

/**
 * Domain model for the payment summary shown on the handoff screen.
 * Mapped from [com.rusuh.app.data.remote.dto.PaymentViewDto].
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
    val currentSnap: SnapTransaction?,
    val expiresAt: Instant,
    val serverNow: Instant,
    val snapClientKey: String,
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
 * Mapped from [com.rusuh.app.data.remote.dto.SnapTransactionDto].
 */
data class SnapTransaction(
    val attemptId: Int,
    val orderId: String,
    val method: PaymentMethod?,
    val snapToken: String,
    val redirectUrl: String,
    val gatewayExpiresAt: Instant,
)

/**
 * Payment method as defined in the API contract.
 * The Snap page shows a subset based on the backend config.
 */
enum class PaymentMethod {
    QRIS,
    GOPAY,
    SHOPEEPAY,
    BCA_VA,
    MANDIRI_VA,
    BNI_VA,
    BRI_VA,
    PERMATA_VA,
    CIMB_VA,
    CARD,
    ;

    companion object {
        /** Parse from wire value (lowercase), null for unseen strings. */
        fun fromWire(value: String?): PaymentMethod? =
            value?.let { v -> entries.firstOrNull { it.name.equals(v, ignoreCase = true) } }
    }
}

/**
 * Result of polling `GET /core/payments/{bookingId}/status`.
 * Mapped from [com.rusuh.app.data.remote.dto.PaymentStatusDto].
 */
data class PaymentStatusResult(
    val bookingStatus: BookingStatus,
    val paymentStatus: PaymentStatus,
    val returnUrl: String?,
    val serverNow: Instant,
)

/**
 * Booking status as defined in the API contract.
 * SELECTION (5 min) → PAYMENT_PENDING (10 min) → PAID / EXPIRED / CANCELLED.
 */
enum class BookingStatus {
    SELECTION,
    PAYMENT_PENDING,
    HELD,
    PAID,
    EXPIRED,
    CANCELLED,
    ;

    companion object {
        fun fromWire(value: String?): BookingStatus =
            value?.let { v -> entries.firstOrNull { it.name.equals(v, ignoreCase = true) } } ?: SELECTION
    }

    /** True when the booking is in a final state (no more changes possible). */
    val isFinal: Boolean get() = this in setOf(PAID, EXPIRED, CANCELLED)

    /** True when the payment arrived in time and tickets are issued. */
    val isPaid: Boolean get() = this == PAID
}

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
