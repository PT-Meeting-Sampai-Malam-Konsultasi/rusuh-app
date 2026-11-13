package com.rusuh.app.feature.eticket

/**
 * A calendar event representation ready to be passed to the platform calendar.
 * All times are in milliseconds since the epoch. The [timeZoneId] is a zone identifier
 * like "Asia/Jakarta" suitable for the platform APIs.
 *
 * @param title Event title, e.g. "SAMUDRA".
 * @param location Venue name, e.g. "GBK" or "Gelora Bung Karno".
 * @param beginMillis Start time in epoch milliseconds (doors open or event start).
 * @param endMillis End time in epoch milliseconds (reasonable default if not specified).
 * @param timeZoneId Time zone identifier for the event, e.g. "Asia/Jakarta".
 */
data class CalendarEvent(
    val title: String,
    val location: String,
    val beginMillis: Long,
    val endMillis: Long,
    val timeZoneId: String,
)

private const val DEFAULT_EVENT_DURATION_MILLIS = 3L * 60 * 60 * 1000 // 3 hours

/**
 * Builds a [CalendarEvent] from the e-ticket order for adding to the device calendar.
 * Uses doors-open time as the start, with a default 3-hour duration.
 */
fun ETicketOrder.toCalendarEvent(): CalendarEvent =
    CalendarEvent(
        title = eventName,
        location = venueShort,
        beginMillis = doorsOpenEpochMillis,
        endMillis = doorsOpenEpochMillis + DEFAULT_EVENT_DURATION_MILLIS,
        timeZoneId = zone.toTimeZoneId(),
    )

/** Maps the Indonesian time zone enum to a standard time zone identifier. */
fun EventZone.toTimeZoneId(): String =
    when (this) {
        EventZone.Wib -> "Asia/Jakarta"
        EventZone.Wita -> "Asia/Makassar"
        EventZone.Wit -> "Asia/Jayapura"
    }
