package com.rusuh.app.data.remote.mapper

import com.rusuh.app.data.clock.parseRfc3339Millis
import com.rusuh.app.data.network.AppLanguage
import com.rusuh.app.data.remote.dto.EventDto
import com.rusuh.app.feature.home.HomeEventItem
import com.rusuh.app.feature.waitingroom.domain.model.QueueEventInfo
import com.rusuh.app.ui.components.EventFormat
import com.rusuh.app.ui.components.EventPosterTone
import com.rusuh.app.ui.components.EventStatus
import com.rusuh.app.ui.components.TimeZoneLabel

/** Wire values of `EventState`. */
private object EventStates {
    const val COMING_SOON = "coming_soon"
    const val WAITING_ROOM_OPEN = "waiting_room_open"
    const val ON_SALE = "on_sale"
    const val ALMOST_GONE = "almost_gone"
    const val TEMPORARILY_FULL = "temporarily_full"
    const val SOLD_OUT = "sold_out"
    const val ENDED = "ended"
}

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Maps an event of `GET /core/events` to the Home card model, or null when it cannot be shown because its id or name is
 * blank or its state is unknown. [nowMillis] is the SERVER
 * time (the response's `serverNow`), used for the coming-soon countdown; null (unknown) shows the event date instead.
 * Times are shown in the venue's zone.
 *
 * shortcut: a sold-out event without `capacity` shows 1 as its capacity (the badge model requires a positive one),
 * a coming-soon event without `saleOpensAt` shows the event date in place of a countdown, and an ended one without
 * `saleEndsAt` shows the event date as the close date. The contract marks these members optional but the read model
 * always sets them for these states.
 */
internal fun EventDto.toHomeEventItem(
    language: AppLanguage,
    nowMillis: Long?,
): HomeEventItem? {
    val status = toStatus(language, nowMillis)
    return if (eventId.isBlank() || name.isBlank() || status == null) {
        null
    } else {
        HomeEventItem(
            id = eventId,
            title = name,
            subtitle = subtitle,
            format = format.toEventFormat(),
            venue = venue,
            city = city,
            dateTime = startMoment()?.fullDateTime(language.words()).orEmpty(),
            timeZone = zoneLabelFor(timezone),
            status = status,
            priceFrom = priceFrom?.let { formatRupiah(it.amount) },
            posterTone = posterTone(status),
        )
    }
}

/** The Waiting Room poster copy of this event. */
internal fun EventDto.toQueueEventInfo(language: AppLanguage): QueueEventInfo {
    val words = language.words()
    val zone = zoneLabelFor(timezone)
    return QueueEventInfo(
        title = name.ifBlank { eventId },
        subtitle = subtitle,
        dateTimeLine = startMoment()?.let { "${it.fullDateTime(words)} ${zone.name}" }.orEmpty(),
        venueLine = listOf(venue, city).filter { it.isNotBlank() }.joinToString(", "),
    )
}

/** `12 Des 2026 · Stadion Madya, Jakarta`, the My Queues second line. */
internal fun EventDto.toDateVenueLine(language: AppLanguage): String {
    val date = startMoment()?.dayMonthYear(language.words())
    val place = listOf(venue, city).filter { it.isNotBlank() }.joinToString(", ")
    return listOfNotNull(date, place.ifBlank { null }).joinToString(" · ")
}

private fun EventDto.startMoment(): LocalMoment? =
    parseRfc3339Millis(startsAt)?.let { localMoment(it, zoneLabelFor(timezone)) }

private fun EventDto.toStatus(
    language: AppLanguage,
    nowMillis: Long?,
): EventStatus? {
    val words = language.words()
    val zone = zoneLabelFor(timezone)
    return when (state) {
        EventStates.COMING_SOON -> comingSoon(words, zone, nowMillis)
        EventStates.WAITING_ROOM_OPEN -> EventStatus.WaitingRoomOpen
        EventStates.ON_SALE -> EventStatus.OnSale
        EventStates.ALMOST_GONE -> EventStatus.AlmostGone
        EventStates.TEMPORARILY_FULL -> EventStatus.TemporarilyFull
        EventStates.SOLD_OUT -> soldOut()
        EventStates.ENDED -> ended(words, zone)
        else -> null
    }
}

private fun EventDto.comingSoon(
    words: FormatWords,
    zone: TimeZoneLabel,
    nowMillis: Long?,
): EventStatus {
    val opensAt = saleOpensAt?.let(::parseRfc3339Millis)
    return if (opensAt == null || nowMillis == null) {
        EventStatus.ComingSoon(countdown = startMoment()?.dayMonthYear(words) ?: eventId)
    } else {
        EventStatus.ComingSoon(
            countdown = formatRemaining(opensAt - nowMillis, words),
            opensAt = localMoment(opensAt, zone).shortDateTimeWithZone(words, zone),
        )
    }
}

private fun EventDto.soldOut(): EventStatus {
    val soldAt = soldOutAt?.let(::parseRfc3339Millis)
    val openedAt = saleOpensAt?.let(::parseRfc3339Millis)
    val minutes = if (soldAt != null && openedAt != null) ((soldAt - openedAt) / MILLIS_PER_MINUTE) else 0L
    return EventStatus.SoldOut(
        soldOutInMinutes = minutes.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        capacity = capacity?.takeIf { it > 0 } ?: 1,
    )
}

private fun EventDto.ended(
    words: FormatWords,
    zone: TimeZoneLabel,
): EventStatus {
    val closed = (saleEndsAt?.let(::parseRfc3339Millis) ?: parseRfc3339Millis(startsAt))?.let { localMoment(it, zone) }
    return EventStatus.Ended(closedOn = closed?.dayMonth(words) ?: eventId)
}

private fun String.toEventFormat(): EventFormat =
    EventFormat.entries.firstOrNull { it != EventFormat.UNKNOWN && it.label.equals(trim(), ignoreCase = true) }
        ?: EventFormat.UNKNOWN

/** Finished events are inked out; the others get a stable colour from the id, so a card never changes colour. */
private fun EventDto.posterTone(status: EventStatus): EventPosterTone =
    if (status is EventStatus.SoldOut || status is EventStatus.Ended) {
        EventPosterTone.Ink
    } else {
        val tones = EventPosterTone.entries.filter { it != EventPosterTone.Ink }
        tones[eventId.fold(0) { acc, char -> (acc * HASH_MULTIPLIER + char.code) and HASH_MASK } % tones.size]
    }

private const val HASH_MULTIPLIER = 31
private const val HASH_MASK = 0x7fffffff
