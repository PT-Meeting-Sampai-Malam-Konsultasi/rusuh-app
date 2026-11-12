package com.rusuh.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Answer of `GET /core/events`. */
@Serializable
data class EventsResponseDto(
    @SerialName("items") val items: List<EventDto> = emptyList(),
    @SerialName("serverNow") val serverNow: String? = null,
)

/** `Money` schema: whole rupiah. */
@Serializable
data class MoneyDto(
    @SerialName("amount") val amount: Long = 0L,
    @SerialName("currency") val currency: String = "IDR",
)

/** `EventSummary` schema. Every member has a default so a partial body still decodes; the mapper judges validity. */
@Serializable
@Suppress("LongParameterList") // mirrors the wire schema
data class EventDto(
    @SerialName("eventId") val eventId: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("subtitle") val subtitle: String = "",
    @SerialName("format") val format: String = "",
    @SerialName("venue") val venue: String = "",
    @SerialName("venueShort") val venueShort: String? = null,
    @SerialName("city") val city: String = "",
    @SerialName("timezone") val timezone: String = "",
    @SerialName("startsAt") val startsAt: String = "",
    @SerialName("doorsOpenAt") val doorsOpenAt: String? = null,
    @SerialName("waitingRoomOpensAt") val waitingRoomOpensAt: String? = null,
    @SerialName("saleOpensAt") val saleOpensAt: String? = null,
    @SerialName("saleEndsAt") val saleEndsAt: String? = null,
    @SerialName("state") val state: String = "",
    @SerialName("priceFrom") val priceFrom: MoneyDto? = null,
    @SerialName("minPrice") val minPrice: MoneyDto? = null,
    @SerialName("capacity") val capacity: Int? = null,
    @SerialName("soldOutAt") val soldOutAt: String? = null,
    @SerialName("imageUrl") val imageUrl: String? = null,
)
