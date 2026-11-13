package com.rusuh.app.feature.eticket

import kotlinx.datetime.LocalDate

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_DAY = 86_400_000L
private const val MINUTES_PER_HOUR = 60
private const val DAYS_FROM_1970_TO_MONDAY_ORIGIN = 3 // 1970-01-01 was a Thursday, index 3 with Monday = 0
private const val DAYS_PER_WEEK = 7

/** Maps the domain order onto what [ETicketScreen] renders. */
internal fun ETicketOrder.toUi(): ETicketUi {
    val doorsOpen = momentOf(doorsOpenEpochMillis, zone)
    val tones = toneByCategory(tickets)
    return ETicketUi(
        orderId = orderId,
        eventName = eventName,
        venueShort = venueShort,
        paidAt = momentOf(paidAtEpochMillis, zone),
        orderTotal = totalLabel,
        paymentMethod = paymentMethod,
        contactEmail = contactEmail,
        tickets =
            tickets.mapIndexed { index, ticket ->
                ETicketCardUi(
                    position = index + 1,
                    total = tickets.size,
                    ticketId = ticket.ticketId,
                    ticketCode = ticket.ticketCode,
                    eventName = eventName,
                    category = ticket.categoryName,
                    tone = tones.getValue(ticket.categoryName),
                    nikMasked = ticket.nikMasked,
                    entryGate = ticket.entryGate,
                    seating = ticket.seating,
                    doorsOpen = doorsOpen,
                    qrPayload = ticket.qrPayload,
                )
            },
    )
}

/** Alternates blue and pink over the distinct categories in first-seen order. */
internal fun toneByCategory(tickets: List<ETicket>): Map<String, TicketTone> =
    tickets.map { it.categoryName }.distinct().withIndex()
        .associate { (index, name) -> name to if (index % 2 == 0) TicketTone.Blue else TicketTone.Pink }

/** Wall-clock parts of [epochMillis] in [zone]. */
internal fun momentOf(
    epochMillis: Long,
    zone: EventZone,
): EventMoment {
    val localMillis = epochMillis + zone.offsetMinutes * MILLIS_PER_MINUTE
    val days = localMillis.floorDiv(MILLIS_PER_DAY)
    val minuteOfDay = localMillis.mod(MILLIS_PER_DAY) / MILLIS_PER_MINUTE
    val (month, day) = monthAndDay(days)
    return EventMoment(
        weekdayIndex = (days + DAYS_FROM_1970_TO_MONDAY_ORIGIN).mod(DAYS_PER_WEEK.toLong()).toInt(),
        day = day,
        monthIndex = month - 1,
        hour = (minuteOfDay / MINUTES_PER_HOUR).toInt(),
        minute = (minuteOfDay % MINUTES_PER_HOUR).toInt(),
        zoneLabel = zone.label,
    )
}

/** Hour as two digits, e.g. "04". */
internal fun EventMoment.hourText(): String = hour.toString().padStart(2, '0')

/** Minute as two digits, e.g. "07". */
internal fun EventMoment.minuteText(): String = minute.toString().padStart(2, '0')

/** Gate and seating joined for display, skipping blanks: "Gerbang 5 · berdiri". */
internal fun entryText(
    gate: String,
    seatingLabel: String?,
    separator: String,
): String =
    listOfNotNull(
        gate.takeIf {
            it.isNotBlank()
        },
        seatingLabel?.takeIf { it.isNotBlank() },
    ).joinToString(separator)

/** Range of [bold] inside [text] for emphasis, or null when it is blank or absent. */
internal fun boldRange(
    text: String,
    bold: String,
): IntRange? {
    if (bold.isBlank()) return null
    val start = text.indexOf(bold)
    return if (start < 0) null else start until start + bold.length
}

/** (month 1..12, day of month) for [daysSinceEpoch] in the proleptic Gregorian calendar. */
private fun monthAndDay(daysSinceEpoch: Long): Pair<Int, Int> {
    val date = LocalDate.fromEpochDays(daysSinceEpoch.toInt())
    return date.monthNumber to date.dayOfMonth
}
