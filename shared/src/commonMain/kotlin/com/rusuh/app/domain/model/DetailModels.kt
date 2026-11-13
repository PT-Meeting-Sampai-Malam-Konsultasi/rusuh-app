package com.rusuh.app.domain.model

/** Event header data for the Detail Ticket page. Display strings are pre-formatted/localized. */
data class EventDetail(
    val id: String,
    val title: String,
    val subtitle: String?,
    val meta: String,
)

/** One capacity-only seat category (no seat map). [stock] is the remaining capacity snapshot. */
data class SeatCategory(
    val id: String,
    val name: String,
    val description: String?,
    val priceRupiah: Long,
    val stock: Int,
    val entryGate: String? = null,
    val availability: CategoryAvailability = CategoryAvailability.AVAILABLE,
)

/** Stock availability status for a category. */
enum class CategoryAvailability {
    AVAILABLE,
    ALMOST_GONE,
    SOLD_OUT,
    ;

    companion object {
        fun fromWire(value: String?): CategoryAvailability =
            when (value?.lowercase()) {
                "available" -> AVAILABLE
                "almost_gone" -> ALMOST_GONE
                "sold_out" -> SOLD_OUT
                else -> AVAILABLE
            }
    }
}

/** The signed-in account. It carries NO NIK: the NIK is always typed per ticket. */
data class AccountProfile(
    val name: String,
    val email: String,
)

/**
 * Everything the Detail page needs in one load.
 *
 * @property adminFeeRupiah ONE fixed fee per ORDER (ADR-0010), not per ticket.
 * @property reservedUnits units the queue admission reserved for this user.
 * @property expiresAtMillis end of the order window in server epoch millis (FR-17).
 */
data class DetailOffering(
    val event: EventDetail,
    val categories: List<SeatCategory>,
    val profile: AccountProfile,
    val adminFeeRupiah: Long,
    val reservedUnits: Int,
    val expiresAtMillis: Long,
)

/** One ticket of a booking request: category plus the holder (name + NIK as on the KTP). */
data class BookingTicket(
    val categoryId: String,
    val holderName: String,
    val holderNik: String,
    /** Validation token from server validation, required for booking. */
    val validationToken: String = "",
)

data class BookingRequest(
    val eventId: String,
    val tickets: List<BookingTicket>,
)

/** A created booking / stock hold; payment continues from [bookingId]. */
data class Booking(
    val bookingId: String,
    val eventId: String = "",
    val status: BookingStatus = BookingStatus.SELECTION,
    val orderCode: String? = null,
    val expiresAtMillis: Long? = null,
    val totalRupiah: Long = 0,
)

/** Booking status as defined by the API. */
enum class BookingStatus {
    SELECTION,
    PAYMENT_PENDING,
    PAID,
    EXPIRED,
    CANCELLED,
    REFUND_PENDING,
    REFUNDED,
    ;

    companion object {
        fun fromWire(value: String?): BookingStatus = entries.firstOrNull { it.name == value } ?: SELECTION
    }
}

/** Outcome of creating a booking. */
sealed interface BookingResult {
    data class Success(val booking: Booking) : BookingResult

    /** Ticket quantity exceeds admission allowance. */
    data object QuantityOverReservation : BookingResult

    /** A selected category is sold out. [categoryId] identifies which. */
    data class CategorySoldOut(val categoryId: String?) : BookingResult

    /** User already has an active booking for this event (one at a time). */
    data object AccountHasActiveBooking : BookingResult

    /** One of the attendees was not properly validated. */
    data object AttendeeNotValidated : BookingResult

    /** A NIK already holds the max seats for this event. */
    data object NikSeatTaken : BookingResult

    /** Duplicate NIK in the order (server-side check). */
    data object DuplicateNik : BookingResult

    /** The booking was not in a held state (expired or already moved on). */
    data object BookingNotHeld : BookingResult

    /** Admission token expired or invalid (back to waiting room). */
    data object AdmissionInvalid : BookingResult

    /** Session expired (401, back to sign-in or waiting room). */
    data object SessionExpired : BookingResult

    /** Network error. */
    data object NetworkError : BookingResult

    /** Rate limited. */
    data class RateLimited(val retryAfterSeconds: Int?) : BookingResult

    /** Any other server error. */
    data class ServerError(val code: Int, val errorCode: ErrorCode) : BookingResult
}
