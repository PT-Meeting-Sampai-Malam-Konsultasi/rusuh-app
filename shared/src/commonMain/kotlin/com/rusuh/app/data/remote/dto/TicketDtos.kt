package com.rusuh.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Answer of `GET /core/tickets`. */
@Serializable
data class TicketsResponseDto(
    @SerialName("items") val items: List<TicketDto> = emptyList(),
)

/**
 * `Ticket` schema. Every member has a default so a partial body still decodes.
 * [qrPayload] is security sensitive: never logged, and [toString] redacts it.
 */
@Serializable
data class TicketDto(
    @SerialName("ticketId") val ticketId: String = "",
    @SerialName("ticketCode") val ticketCode: String = "",
    @SerialName("bookingId") val bookingId: String = "",
    @SerialName("eventId") val eventId: String = "",
    @SerialName("categoryId") val categoryId: String = "",
    @SerialName("categoryName") val categoryName: String = "",
    @SerialName("seating") val seating: String? = null,
    @SerialName("entryGate") val entryGate: String = "",
    @SerialName("holderName") val holderName: String = "",
    @SerialName("nikMasked") val nikMasked: String = "",
    @SerialName("qrPayload") val qrPayload: String = "",
    @SerialName("issuedAt") val issuedAt: String = "",
) {
    /** Redacts [qrPayload] to prevent leaking security-sensitive data in logs. */
    override fun toString(): String =
        "TicketDto(ticketId=$ticketId, ticketCode=$ticketCode, bookingId=$bookingId, " +
            "eventId=$eventId, categoryId=$categoryId, categoryName=$categoryName, " +
            "seating=$seating, entryGate=$entryGate, holderName=$holderName, " +
            "nikMasked=$nikMasked, qrPayload=***, issuedAt=$issuedAt)"
}
