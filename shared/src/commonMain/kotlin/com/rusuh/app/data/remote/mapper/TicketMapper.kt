package com.rusuh.app.data.remote.mapper

import com.rusuh.app.data.cache.CachedEventFacts
import com.rusuh.app.data.clock.parseRfc3339Millis
import com.rusuh.app.data.network.AppLanguage
import com.rusuh.app.data.remote.dto.EventDto
import com.rusuh.app.data.remote.dto.TicketDto
import com.rusuh.app.feature.eticket.ETicket
import com.rusuh.app.feature.eticket.ETicketOrder
import com.rusuh.app.feature.eticket.EventZone
import com.rusuh.app.feature.eticket.Seating
import com.rusuh.app.feature.tickets.PosterTone
import com.rusuh.app.feature.tickets.TicketAttendee
import com.rusuh.app.feature.tickets.TicketOrder
import com.rusuh.app.ui.components.TimeZoneLabel

/**
 * Groups flat tickets by `bookingId` into orders with display-ready event facts.
 *
 * shortcut: `GET /core/tickets` does not include order number, total, or payment method, so those fields are
 * left blank/placeholder. The domain model has them for UI parity with the original stub; the UI will show
 * what the API provides. See docs for the full contract gaps.
 */
internal fun groupTicketsIntoOrders(
    tickets: List<TicketDto>,
    eventFactsProvider: (String) -> CachedEventFacts?,
    language: AppLanguage,
    nowMillis: Long?,
): List<TicketOrder> {
    val byBooking = tickets.filter { it.bookingId.isNotBlank() }.groupBy { it.bookingId }
    return byBooking.map { (bookingId, ticketGroup) ->
        val first = ticketGroup.first()
        val eventFacts = eventFactsProvider(first.eventId)
        ticketGroupToOrder(bookingId, ticketGroup, eventFacts, language, nowMillis)
    }
}

private fun ticketGroupToOrder(
    bookingId: String,
    tickets: List<TicketDto>,
    eventFacts: CachedEventFacts?,
    language: AppLanguage,
    nowMillis: Long?,
): TicketOrder {
    val first = tickets.first()
    val eventName = eventFacts?.eventName ?: first.eventId
    val venueShort = eventFacts?.venueShort.orEmpty()
    val startsAtMillis = eventFacts?.startsAtMillis
    val zone = eventFacts?.timezone?.let { zoneLabelFor(it) } ?: TimeZoneLabel.WIB
    val words = language.words()

    val dateVenue = buildDateVenueLine(startsAtMillis, zone, venueShort, words)
    val (dayLabel, monthLabel) = extractDayMonthLabels(startsAtMillis, zone, words)
    val isPast = startsAtMillis != null && nowMillis != null && startsAtMillis < nowMillis
    val countdownLabel =
        if (!isPast && startsAtMillis != null && nowMillis != null) {
            formatRemaining(startsAtMillis - nowMillis, words)
        } else {
            null
        }

    return TicketOrder(
        orderId = bookingId,
        eventTitle = eventName,
        dateVenue = dateVenue,
        posterLabel = eventName.uppercase(),
        posterTone = posterToneFor(first.eventId),
        attendees = tickets.map { TicketAttendee(it.categoryName, it.holderName) },
        // shortcut: these fields are not in the Ticket schema
        totalLabel = "",
        dayLabel = dayLabel,
        monthLabel = monthLabel,
        startsAtEpochMillis = startsAtMillis ?: 0L,
        countdownLabel = countdownLabel,
        isPast = isPast,
    )
}

private fun buildDateVenueLine(
    startsAtMillis: Long?,
    zone: TimeZoneLabel,
    venueShort: String,
    words: FormatWords,
): String {
    val datePart =
        startsAtMillis?.let {
            val moment = localMoment(it, zone)
            "${moment.fullDateTime(words)} ${zone.name}"
        }
    return listOfNotNull(datePart, venueShort.takeIf { it.isNotBlank() }).joinToString(" · ")
}

private fun extractDayMonthLabels(
    startsAtMillis: Long?,
    zone: TimeZoneLabel,
    words: FormatWords,
): Pair<String, String> {
    if (startsAtMillis == null) return "" to ""
    val moment = localMoment(startsAtMillis, zone)
    val monthLabel = words.months.getOrElse(moment.month - 1) { "" }.uppercase()
    return moment.day.toString() to monthLabel
}

/** Stable poster tone from the event id. */
private fun posterToneFor(eventId: String): PosterTone {
    val tones = PosterTone.entries
    val hash = eventId.fold(0) { acc, char -> (acc * 31 + char.code) and 0x7fffffff }
    return tones[hash % tones.size]
}

/**
 * Converts a ticket group into an [ETicketOrder] for the e-ticket screen.
 *
 * shortcut: order total, payment method, paid-at time, and contact email are not in the Ticket schema.
 * They are left as placeholders; the UI handles missing data gracefully.
 */
internal fun ticketGroupToETicketOrder(
    bookingId: String,
    tickets: List<TicketDto>,
    eventFacts: CachedEventFacts?,
): ETicketOrder {
    val first = tickets.first()
    val eventName = eventFacts?.eventName ?: first.eventId
    val venueShort = eventFacts?.venueShort.orEmpty()
    val doorsOpenMillis = eventFacts?.doorsOpenAtMillis ?: 0L
    val timezone = eventFacts?.timezone ?: "Asia/Jakarta"
    val zone = EventZone.fromLabel(zoneLabel(timezone))

    return ETicketOrder(
        orderId = bookingId,
        eventName = eventName,
        venueShort = venueShort,
        doorsOpenEpochMillis = doorsOpenMillis,
        // shortcut: not in Ticket schema
        paidAtEpochMillis = 0L,
        zone = zone,
        // shortcut: not in Ticket schema
        totalLabel = "",
        paymentMethod = "",
        contactEmail = "",
        tickets = tickets.map { it.toETicket() },
    )
}

private fun TicketDto.toETicket(): ETicket =
    ETicket(
        ticketId = ticketId,
        ticketCode = ticketCode,
        categoryName = categoryName,
        seating = Seating.fromApi(seating),
        entryGate = entryGate,
        holderName = holderName,
        nikMasked = nikMasked,
        qrPayload = qrPayload,
    )

/** Extracts [CachedEventFacts] from an event DTO. */
internal fun EventDto.toCachedEventFacts(): CachedEventFacts =
    CachedEventFacts(
        eventName = name.ifBlank { eventId },
        venueShort = venueShort.orEmpty(),
        timezone = timezone,
        doorsOpenAtMillis = doorsOpenAt?.let(::parseRfc3339Millis),
        startsAtMillis = parseRfc3339Millis(startsAt),
    )

/** Converts IANA timezone to zone label string. */
private fun zoneLabel(timezone: String): String =
    when (zoneLabelFor(timezone)) {
        TimeZoneLabel.WIB -> "WIB"
        TimeZoneLabel.WITA -> "WITA"
        TimeZoneLabel.WIT -> "WIT"
    }
