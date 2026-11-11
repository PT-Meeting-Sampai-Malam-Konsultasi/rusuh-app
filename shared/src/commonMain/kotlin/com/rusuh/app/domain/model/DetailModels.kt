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
)

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
)

data class BookingRequest(
    val eventId: String,
    val tickets: List<BookingTicket>,
)

/** A created booking / stock hold; payment continues from [bookingId]. */
data class Booking(
    val bookingId: String,
)
