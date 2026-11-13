package com.rusuh.app.data.remote.mapper

import com.rusuh.app.ui.components.TimeZoneLabel
import kotlinx.datetime.Instant
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

// Display moments in the fixed Indonesian zones (no DST), never the device clock or the device time zone.

private const val DAYS_PER_WEEK = 7
private const val WIB_OFFSET_HOURS = 7
private const val WITA_OFFSET_HOURS = 8
private const val WIT_OFFSET_HOURS = 9
private const val MINUTES_PER_HOUR = 60

/** Wall-clock fields of an instant in a fixed UTC offset. [weekday] is 0 = Sunday. */
internal data class LocalMoment(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val weekday: Int,
)

/** The Indonesian display zone of an IANA [timezone]; unknown or blank is [TimeZoneLabel.WIB]. */
internal fun zoneLabelFor(timezone: String): TimeZoneLabel =
    when (timezone.trim()) {
        "Asia/Makassar", "Asia/Ujung_Pandang" -> TimeZoneLabel.WITA
        "Asia/Jayapura" -> TimeZoneLabel.WIT
        else -> TimeZoneLabel.WIB
    }

internal fun TimeZoneLabel.offsetMinutes(): Int =
    when (this) {
        TimeZoneLabel.WIB -> WIB_OFFSET_HOURS
        TimeZoneLabel.WITA -> WITA_OFFSET_HOURS
        TimeZoneLabel.WIT -> WIT_OFFSET_HOURS
    } * MINUTES_PER_HOUR

/** The wall-clock fields of [epochMillis] in [zone]. */
internal fun localMoment(
    epochMillis: Long,
    zone: TimeZoneLabel,
): LocalMoment {
    val offset = UtcOffset(minutes = zone.offsetMinutes())
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(offset.asTimeZone())
    return LocalMoment(
        year = local.year,
        month = local.monthNumber,
        day = local.dayOfMonth,
        hour = local.hour,
        minute = local.minute,
        weekday = local.dayOfWeek.isoDayNumber % DAYS_PER_WEEK,
    )
}
