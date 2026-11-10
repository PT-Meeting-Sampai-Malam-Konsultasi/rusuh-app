package com.rusuh.app.feature.tickets

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Data seam for the Tiket tab, the contract TICKET-00-M4 implements against the
 * real backend (`GET /tickets`).
 */
interface TicketsRepository {
    /** The signed-in user's purchased orders, in no particular order. */
    suspend fun getMyOrders(): List<TicketOrder>
}

/**
 * Stub implementation that parses a JSON string, so the use case, mapper and UI
 * run against realistic data before the backend exists.
 *
 * shortcut: the JSON shape below is our own guess (orders with attendees and
 * pre-formatted labels), NOT the API's flat `Ticket` list. Replace this class with
 * the network-backed one and regroup tickets by `bookingId` once BE is known.
 */
class StubTicketsRepository(
    private val json: String = StubTicketsJson.ORDERS,
) : TicketsRepository {
    override suspend fun getMyOrders(): List<TicketOrder> =
        lenientJson.decodeFromString<StubOrdersDto>(json).orders
            .filter { it.orderId.isNotBlank() } // drop bad rows at the boundary
            .map { it.toDomain() }

    private companion object {
        val lenientJson = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class StubOrdersDto(
    val orders: List<StubOrderDto> = emptyList(),
)

@Serializable
internal data class StubOrderDto(
    val orderId: String,
    val eventTitle: String,
    val dateVenue: String,
    val posterLabel: String = "",
    val posterTone: PosterTone = PosterTone.Pink,
    val attendees: List<StubAttendeeDto> = emptyList(),
    val totalLabel: String,
    val dayLabel: String,
    val monthLabel: String,
    val startsAtEpochMillis: Long,
    val countdownLabel: String? = null,
    @SerialName("past") val isPast: Boolean = false,
)

@Serializable
internal data class StubAttendeeDto(
    val category: String,
    val holderName: String,
)

private fun StubOrderDto.toDomain(): TicketOrder =
    TicketOrder(
        orderId = orderId,
        eventTitle = eventTitle,
        dateVenue = dateVenue,
        posterLabel = posterLabel,
        posterTone = posterTone,
        attendees = attendees.map { TicketAttendee(it.category, it.holderName) },
        totalLabel = totalLabel,
        dayLabel = dayLabel,
        monthLabel = monthLabel,
        startsAtEpochMillis = startsAtEpochMillis,
        countdownLabel = countdownLabel,
        isPast = isPast,
    )

/** Stub payloads mirroring the Figma "Event Purchased" frame. */
internal object StubTicketsJson {
    val ORDERS =
        """
        {"orders":[
          {"orderId":"RSH-26-084731","eventTitle":"Samudra – Tur Gelombang 2026",
           "dateVenue":"Sab, 12 Des 2026 · 19.00 WIB · Stadion Utama GBK, Jakarta","posterLabel":"SAMUDRA","posterTone":"Blue",
           "attendees":[{"category":"Festival B","holderName":"Nadia Saputri"},
                        {"category":"Festival B","holderName":"Rina Wulandari"},
                        {"category":"Tribun Atas","holderName":"Dimas Pratama"}],
           "totalLabel":"Rp 2.180.000","dayLabel":"12","monthLabel":"DES",
           "startsAtEpochMillis":1797076800000,"countdownLabel":"33 hari lagi"},
          {"orderId":"RSH-26-071205","eventTitle":"Pelita – Tur Kota Lama",
           "dateVenue":"Jum, 4 Des 2026 · 19.30 WIB · Lapangan Simpang Lima, Semarang","posterLabel":"PELITA",
           "attendees":[{"category":"Festival A","holderName":"Nadia Saputri"},
                        {"category":"Festival A","holderName":"Bagas Hidayat"}],
           "totalLabel":"Rp 1.350.000","dayLabel":"4","monthLabel":"DES",
           "startsAtEpochMillis":1796387400000,"countdownLabel":"25 hari lagi"},
          {"orderId":"RSH-26-052118","eventTitle":"Kelana – Tur Pulau",
           "dateVenue":"Sab, 31 Okt 2026 · 19.00 WITA · Lapangan Puputan, Denpasar","posterLabel":"KELANA","posterTone":"Grey",
           "attendees":[{"category":"Festival","holderName":"Nadia Saputri"}],
           "totalLabel":"Rp 450.000","dayLabel":"31","monthLabel":"OKT",
           "startsAtEpochMillis":1793448000000,"past":true}
        ]}
        """.trimIndent()

    const val EMPTY = """{"orders":[]}"""
}
