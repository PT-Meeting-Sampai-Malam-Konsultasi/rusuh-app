package com.rusuh.app.data.remote.mapper

import com.rusuh.app.ui.components.TimeZoneLabel

// Wall-clock arithmetic on epoch millis in the fixed Indonesian zones: no datetime library, and never the device
// clock or the device time zone.

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val DAYS_PER_WEEK = 7
private const val EPOCH_WEEKDAY = 4 // 1970-01-01 was a Thursday; 0 = Sunday
private const val MILLIS_PER_DAY = 86_400_000L

// Hinnant's civil_from_days constants.
private const val DAYS_FROM_CIVIL_TO_EPOCH = 719_468L
private const val DAYS_PER_ERA = 146_097L
private const val DAYS_PER_YEAR_CYCLE = 365L
private const val ERA_YEARS = 400L
private const val CYCLE_DIVISOR_4 = 4
private const val CYCLE_DIVISOR_100 = 100
private const val LEAP_ADJUST_DAYS = 1_460L
private const val CENTURY_ADJUST_DAYS = 36_524L
private const val ERA_ADJUST_DAYS = 146_096L
private const val MARCH_PHASE = 153L
private const val MARCH_PHASE_OFFSET = 2L
private const val MARCH_SHIFT = 3
private const val MARCH_BASED_FIRST_MONTHS = 10
private const val MONTHS_PER_YEAR = 12
private const val MARCH_PHASE_SPAN = 5L
private const val WIB_OFFSET_MINUTES = 7 * 60
private const val WITA_OFFSET_MINUTES = 8 * 60
private const val WIT_OFFSET_MINUTES = 9 * 60

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
        TimeZoneLabel.WIB -> WIB_OFFSET_MINUTES
        TimeZoneLabel.WITA -> WITA_OFFSET_MINUTES
        TimeZoneLabel.WIT -> WIT_OFFSET_MINUTES
    }

/** The wall-clock fields of [epochMillis] in [zone]. */
internal fun localMoment(
    epochMillis: Long,
    zone: TimeZoneLabel,
): LocalMoment {
    val shifted = epochMillis + zone.offsetMinutes() * MILLIS_PER_MINUTE
    val days = shifted.floorDiv(MILLIS_PER_DAY)
    val minuteOfDay = shifted.mod(MILLIS_PER_DAY) / MILLIS_PER_MINUTE
    val (year, month, day) = civilFromDays(days)
    return LocalMoment(
        year = year,
        month = month,
        day = day,
        hour = (minuteOfDay / MINUTES_PER_HOUR).toInt(),
        minute = (minuteOfDay % MINUTES_PER_HOUR).toInt(),
        weekday = (days + EPOCH_WEEKDAY).mod(DAYS_PER_WEEK.toLong()).toInt(),
    )
}

/** Inverse of the proleptic Gregorian day count (Howard Hinnant's civil_from_days). */
private fun civilFromDays(epochDays: Long): Triple<Int, Int, Int> {
    val z = epochDays + DAYS_FROM_CIVIL_TO_EPOCH
    val era = z.floorDiv(DAYS_PER_ERA)
    val dayOfEra = z - era * DAYS_PER_ERA
    val yearOfEra =
        (dayOfEra - dayOfEra / LEAP_ADJUST_DAYS + dayOfEra / CENTURY_ADJUST_DAYS - dayOfEra / ERA_ADJUST_DAYS) /
            DAYS_PER_YEAR_CYCLE
    val dayOfYear =
        dayOfEra - (DAYS_PER_YEAR_CYCLE * yearOfEra + yearOfEra / CYCLE_DIVISOR_4 - yearOfEra / CYCLE_DIVISOR_100)
    val marchMonth = (MARCH_PHASE_SPAN * dayOfYear + MARCH_PHASE_OFFSET) / MARCH_PHASE
    val day = (dayOfYear - (MARCH_PHASE * marchMonth + MARCH_PHASE_OFFSET) / MARCH_PHASE_SPAN + 1).toInt()
    val month =
        if (marchMonth < MARCH_BASED_FIRST_MONTHS) {
            marchMonth.toInt() + MARCH_SHIFT
        } else {
            marchMonth.toInt() - MARCH_BASED_FIRST_MONTHS + 1
        }
    val year = (yearOfEra + era * ERA_YEARS).toInt() + if (month <= 2) 1 else 0
    return Triple(year, month.coerceIn(1, MONTHS_PER_YEAR), day)
}
