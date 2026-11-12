package com.rusuh.app.feature.eticket

/** Indonesian time zone an event runs in; the e-ticket shows door and payment times in it. */
enum class EventZone(val label: String, val offsetMinutes: Int) {
    Wib("WIB", 7 * 60),
    Wita("WITA", 8 * 60),
    Wit("WIT", 9 * 60),
    ;

    companion object {
        /** The zone for a label such as "WITA", or [Wib] when it is unknown. */
        fun fromLabel(label: String): EventZone =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: Wib
    }
}

/** API `Ticket.seating` (`standing` / `seated`); [Unknown] when the field is absent or new. */
enum class Seating {
    Standing,
    Seated,
    Unknown,
    ;

    companion object {
        fun fromApi(value: String?): Seating =
            when (value?.lowercase()) {
                "standing" -> Standing
                "seated" -> Seated
                else -> Unknown
            }
    }
}

/**
 * One issued ticket (API `Ticket`). [qrPayload] is the signed string the gate scans; it is the exact
 * content of the QR code and must never be logged. [nikMasked] arrives masked from the API and is shown
 * as is; the app never holds the full NIK.
 */
data class ETicket(
    val ticketId: String,
    val ticketCode: String,
    val categoryName: String,
    val seating: Seating,
    val entryGate: String,
    val holderName: String,
    val nikMasked: String,
    val qrPayload: String,
)

/**
 * A paid order with its issued tickets, what the e-ticket page renders.
 *
 * shortcut: the API `Ticket` carries no event name, venue, door time or payment facts (and `GET /tickets`
 * returns tickets flat, not grouped by order), so the order-level fields here come from the order the Tiket
 * tab already knows (the stub, until TICKET-00-M4). Revisit when the backend contract for event facts and
 * the order lookup is settled.
 *
 * @param eventName short event name shown big on each ticket (e.g. "SAMUDRA").
 * @param venueShort short venue name for the headline (e.g. "GBK").
 * @param doorsOpenEpochMillis when the gate opens.
 * @param paidAtEpochMillis when the payment was confirmed.
 * @param zone zone for door and payment times.
 * @param totalLabel pre-formatted total, e.g. "Rp 2.180.000".
 * @param paymentMethod pre-formatted payment method, e.g. "QRIS".
 * @param contactEmail where the e-ticket email was sent.
 */
data class ETicketOrder(
    val orderId: String,
    val eventName: String,
    val venueShort: String,
    val doorsOpenEpochMillis: Long,
    val paidAtEpochMillis: Long,
    val zone: EventZone,
    val totalLabel: String,
    val paymentMethod: String,
    val contactEmail: String,
    val tickets: List<ETicket>,
)

/** What [GetETicketUseCase] can answer. */
sealed interface ETicketResult {
    data class Found(val order: ETicketOrder) : ETicketResult

    /** No such order, or it has no issued tickets for this account. */
    data object NotFound : ETicketResult
}
