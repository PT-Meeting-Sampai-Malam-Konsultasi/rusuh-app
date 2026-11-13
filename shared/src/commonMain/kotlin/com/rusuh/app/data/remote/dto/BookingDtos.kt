package com.rusuh.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- GET /events/{eventId}/categories ---

/** `GET /core/events/{eventId}/categories` response. */
@Serializable
data class CategoriesResponseDto(
    @SerialName("eventId") val eventId: String = "",
    @SerialName("categories") val categories: List<CategoryDto> = emptyList(),
    @SerialName("maxTicketsPerOrder") val maxTicketsPerOrder: Int = 3,
    @SerialName("maxTickets") val maxTickets: Int = 3,
    @SerialName("adminFee") val adminFee: MoneyDto = MoneyDto(),
    @SerialName("expiresAt") val expiresAt: String = "",
    @SerialName("serverNow") val serverNow: String = "",
)

@Serializable
data class CategoryDto(
    @SerialName("categoryId") val categoryId: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("seating") val seating: String = "",
    @SerialName("entryGate") val entryGate: String? = null,
    @SerialName("price") val price: MoneyDto = MoneyDto(),
    @SerialName("availability") val availability: String = "",
    @SerialName("available") val available: Int = 0,
)

// --- POST /events/{eventId}/attendees/validate ---

/**
 * `POST /core/events/{eventId}/attendees/validate` request.
 *
 * NIK is PII: [toString] redacts it to prevent accidental leaks in logs or error messages.
 */
@Serializable
data class ValidateAttendeeRequestDto(
    @SerialName("fieldIndex") val fieldIndex: Int = 0,
    @SerialName("fullName") val fullName: String = "",
    @SerialName("nik") val nik: String = "",
) {
    override fun toString(): String =
        "ValidateAttendeeRequestDto(fieldIndex=$fieldIndex, fullName=$fullName, nik=<redacted>)"
}

/** `POST /core/events/{eventId}/attendees/validate` response. */
@Serializable
data class ValidateAttendeeResponseDto(
    @SerialName("validationToken") val validationToken: String = "",
    @SerialName("nikMasked") val nikMasked: String = "",
    @SerialName("expiresAt") val expiresAt: String = "",
)

// --- POST /bookings ---

/** `POST /core/bookings` request. */
@Serializable
data class CreateBookingRequestDto(
    @SerialName("eventId") val eventId: String = "",
    @SerialName("tickets") val tickets: List<BookingTicketDto> = emptyList(),
)

@Serializable
data class BookingTicketDto(
    @SerialName("categoryId") val categoryId: String = "",
    @SerialName("validationToken") val validationToken: String = "",
)

// --- Booking response (shared by POST /bookings, GET /bookings/{id}, etc.) ---

@Serializable
data class BookingDto(
    @SerialName("bookingId") val bookingId: String = "",
    @SerialName("eventId") val eventId: String = "",
    @SerialName("status") val status: String = "",
    @SerialName("price") val price: PriceBreakdownDto = PriceBreakdownDto(),
    @SerialName("orderCode") val orderCode: String? = null,
    @SerialName("attendees") val attendees: List<AttendeeDto> = emptyList(),
    @SerialName("expiresAt") val expiresAt: String? = null,
    @SerialName("refundRequired") val refundRequired: Boolean = false,
    @SerialName("tickets") val tickets: List<TicketDto> = emptyList(),
    @SerialName("paidAt") val paidAt: String? = null,
    @SerialName("paymentMethod") val paymentMethod: String? = null,
    @SerialName("createdAt") val createdAt: String = "",
    @SerialName("serverNow") val serverNow: String = "",
)

@Serializable
data class PriceBreakdownDto(
    @SerialName("lines") val lines: List<PriceLineDto> = emptyList(),
    @SerialName("ticketSubtotal") val ticketSubtotal: MoneyDto = MoneyDto(),
    @SerialName("adminFee") val adminFee: MoneyDto = MoneyDto(),
    @SerialName("total") val total: MoneyDto = MoneyDto(),
)

@Serializable
data class PriceLineDto(
    @SerialName("categoryId") val categoryId: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("quantity") val quantity: Int = 0,
    @SerialName("unitPrice") val unitPrice: MoneyDto = MoneyDto(),
    @SerialName("lineTotal") val lineTotal: MoneyDto = MoneyDto(),
)

@Serializable
data class AttendeeDto(
    @SerialName("categoryId") val categoryId: String = "",
    @SerialName("categoryName") val categoryName: String = "",
    @SerialName("fullName") val fullName: String = "",
    @SerialName("nikMasked") val nikMasked: String = "",
)

@Serializable
data class TicketDto(
    @SerialName("ticketId") val ticketId: String = "",
    @SerialName("ticketCode") val ticketCode: String = "",
    @SerialName("bookingId") val bookingId: String = "",
    @SerialName("eventId") val eventId: String = "",
    @SerialName("categoryId") val categoryId: String = "",
    @SerialName("categoryName") val categoryName: String = "",
    @SerialName("seating") val seating: String = "",
    @SerialName("entryGate") val entryGate: String? = null,
    @SerialName("holderName") val holderName: String = "",
    @SerialName("nikMasked") val nikMasked: String = "",
    @SerialName("qrPayload") val qrPayload: String = "",
    @SerialName("issuedAt") val issuedAt: String = "",
)

// --- GET /bookings/active ---

@Serializable
data class ActiveBookingsResponseDto(
    @SerialName("items") val items: List<BookingDto> = emptyList(),
)
