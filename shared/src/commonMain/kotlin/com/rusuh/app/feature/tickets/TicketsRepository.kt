package com.rusuh.app.feature.tickets

import com.rusuh.app.feature.eticket.ETicket
import com.rusuh.app.feature.eticket.ETicketOrder
import com.rusuh.app.feature.eticket.EventZone
import com.rusuh.app.feature.eticket.Seating
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

    /**
     * The issued tickets of order [orderId] with the event facts the e-ticket page needs, or null when the
     * order is unknown. Real backend: `GET /tickets` grouped by `bookingId` (or `GET /tickets/{ticketId}`).
     */
    suspend fun getOrderTickets(orderId: String): ETicketOrder?
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
    override suspend fun getMyOrders(): List<TicketOrder> = stubOrders().map { it.toDomain() }

    override suspend fun getOrderTickets(orderId: String): ETicketOrder? =
        stubOrders().firstOrNull { it.orderId == orderId }?.toETicketOrder()

    private fun stubOrders(): List<StubOrderDto> =
        lenientJson.decodeFromString<StubOrdersDto>(json).orders
            .filter { it.orderId.isNotBlank() } // drop bad rows at the boundary

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
    val venueShort: String = "",
    val doorsOpenEpochMillis: Long = startsAtEpochMillis,
    val paidAtEpochMillis: Long = 0,
    val zone: String = "WIB",
    val paymentMethod: String = "",
    val contactEmail: String = "",
    val tickets: List<StubTicketDto> = emptyList(),
)

@Serializable
internal data class StubTicketDto(
    val ticketId: String,
    val ticketCode: String,
    val categoryName: String,
    val seating: String? = null,
    val entryGate: String = "",
    val holderName: String = "",
    val nikMasked: String = "",
    val qrPayload: String,
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

private fun StubOrderDto.toETicketOrder(): ETicketOrder =
    ETicketOrder(
        orderId = orderId,
        eventName = posterLabel.ifBlank { eventTitle },
        venueShort = venueShort,
        doorsOpenEpochMillis = doorsOpenEpochMillis,
        paidAtEpochMillis = paidAtEpochMillis,
        zone = EventZone.fromLabel(zone),
        totalLabel = totalLabel,
        paymentMethod = paymentMethod,
        contactEmail = contactEmail,
        tickets =
            tickets.map {
                ETicket(
                    ticketId = it.ticketId,
                    ticketCode = it.ticketCode,
                    categoryName = it.categoryName,
                    seating = Seating.fromApi(it.seating),
                    entryGate = it.entryGate,
                    holderName = it.holderName,
                    nikMasked = it.nikMasked,
                    qrPayload = it.qrPayload,
                )
            },
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
           "startsAtEpochMillis":1797076800000,"countdownLabel":"33 hari lagi",
           "venueShort":"GBK","doorsOpenEpochMillis":1797066000000,"paidAtEpochMillis":1794200820000,"zone":"WIB",
           "paymentMethod":"QRIS","contactEmail":"nadia.saputri@example.com",
           "tickets":[
             {"ticketId":"tk_01JB2YBA1","ticketCode":"SMD-FB-04827","categoryName":"Festival B","seating":"standing",
              "entryGate":"Gerbang 5","holderName":"Nadia Saputri","nikMasked":"3174 •••• •••• 0004",
              "qrPayload":"RSH1.SMD-FB-04827.Q2hlY2tJbi1zaWduZWQ"},
             {"ticketId":"tk_01JB2YBA2","ticketCode":"SMD-FB-04828","categoryName":"Festival B","seating":"standing",
              "entryGate":"Gerbang 5","holderName":"Rina Wulandari","nikMasked":"3201 •••• •••• 0017",
              "qrPayload":"RSH1.SMD-FB-04828.Q2hlY2tJbi1zaWduZWQ"},
             {"ticketId":"tk_01JB2YBA3","ticketCode":"SMD-TA-11902","categoryName":"Tribun Atas","seating":"seated",
              "entryGate":"Gerbang 9","holderName":"Dimas Pratama","nikMasked":"3273 •••• •••• 0002",
              "qrPayload":"RSH1.SMD-TA-11902.Q2hlY2tJbi1zaWduZWQ"}]},
          {"orderId":"RSH-26-071205","eventTitle":"Pelita – Tur Kota Lama",
           "dateVenue":"Jum, 4 Des 2026 · 19.30 WIB · Lapangan Simpang Lima, Semarang","posterLabel":"PELITA",
           "attendees":[{"category":"Festival A","holderName":"Nadia Saputri"},
                        {"category":"Festival A","holderName":"Bagas Hidayat"}],
           "totalLabel":"Rp 1.350.000","dayLabel":"4","monthLabel":"DES",
           "startsAtEpochMillis":1796387400000,"countdownLabel":"25 hari lagi",
           "venueShort":"Simpang Lima","doorsOpenEpochMillis":1796378400000,"paidAtEpochMillis":1793500200000,"zone":"WIB",
           "paymentMethod":"GoPay","contactEmail":"nadia.saputri@example.com",
           "tickets":[
             {"ticketId":"tk_01JB3PLT1","ticketCode":"PLT-FA-20311","categoryName":"Festival A","seating":"standing",
              "entryGate":"Gerbang 2","holderName":"Nadia Saputri","nikMasked":"3174 •••• •••• 0004",
              "qrPayload":"RSH1.PLT-FA-20311.Q2hlY2tJbi1zaWduZWQ"},
             {"ticketId":"tk_01JB3PLT2","ticketCode":"PLT-FA-20312","categoryName":"Festival A","seating":"standing",
              "entryGate":"Gerbang 2","holderName":"Bagas Hidayat","nikMasked":"3374 •••• •••• 0031",
              "qrPayload":"RSH1.PLT-FA-20312.Q2hlY2tJbi1zaWduZWQ"}]},
          {"orderId":"RSH-26-052118","eventTitle":"Kelana – Tur Pulau",
           "dateVenue":"Sab, 31 Okt 2026 · 19.00 WITA · Lapangan Puputan, Denpasar","posterLabel":"KELANA","posterTone":"Grey",
           "attendees":[{"category":"Festival","holderName":"Nadia Saputri"}],
           "totalLabel":"Rp 450.000","dayLabel":"31","monthLabel":"OKT",
           "startsAtEpochMillis":1793448000000,"past":true,
           "venueShort":"Puputan","doorsOpenEpochMillis":1793437200000,"paidAtEpochMillis":1791202500000,"zone":"WITA",
           "paymentMethod":"QRIS","contactEmail":"nadia.saputri@example.com",
           "tickets":[
             {"ticketId":"tk_01JB4KLN1","ticketCode":"KLN-FS-30144","categoryName":"Festival","seating":"standing",
              "entryGate":"Gerbang 1","holderName":"Nadia Saputri","nikMasked":"3174 •••• •••• 0004",
              "qrPayload":"RSH1.KLN-FS-30144.Q2hlY2tJbi1zaWduZWQ"}]}
        ]}
        """.trimIndent()

    const val EMPTY = """{"orders":[]}"""
}
