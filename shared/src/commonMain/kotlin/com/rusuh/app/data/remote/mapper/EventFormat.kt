package com.rusuh.app.data.remote.mapper

import com.rusuh.app.data.network.AppLanguage
import com.rusuh.app.ui.components.TimeZoneLabel

// Display formatting for event dates, prices and countdowns. The words live in the per-language tables below (the
// data layer has no Compose resources); everything else is arithmetic on epoch millis, with no datetime library and
// never the device clock or the device time zone.

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val THOUSAND_DIGITS = 3
private const val MINUTES_PER_DAY_CYCLE = 1_440L

/** The words of one language used in event copy. */
@Suppress("LongParameterList") // a table of independent words
internal class FormatWords(
    val weekdays: List<String>,
    val months: List<String>,
    val timeSeparator: Char,
    val day: Pair<String, String>,
    val hour: Pair<String, String>,
    val minute: Pair<String, String>,
    val remainingSuffix: String,
    val lessThanMinute: String,
)

internal fun AppLanguage.words(): FormatWords =
    when (this) {
        AppLanguage.Indonesian -> ID_WORDS
        AppLanguage.English -> EN_WORDS
    }

private val ID_WORDS =
    FormatWords(
        weekdays = listOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab"),
        months = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"),
        timeSeparator = '.',
        day = "hari" to "hari",
        hour = "jam" to "jam",
        minute = "menit" to "menit",
        remainingSuffix = "lagi",
        lessThanMinute = "kurang dari 1 menit lagi",
    )

private val EN_WORDS =
    FormatWords(
        weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
        months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
        timeSeparator = ':',
        day = "day" to "days",
        hour = "hour" to "hours",
        minute = "min" to "min",
        remainingSuffix = "left",
        lessThanMinute = "less than 1 min left",
    )

internal fun LocalMoment.time(words: FormatWords): String =
    "${hour.toString().padStart(2, '0')}${words.timeSeparator}${minute.toString().padStart(2, '0')}"

/** `9 Jan 2027`. */
internal fun LocalMoment.dayMonthYear(words: FormatWords): String = "$day ${words.months[month - 1]} $year"

/** `9 Jan`. */
internal fun LocalMoment.dayMonth(words: FormatWords): String = "$day ${words.months[month - 1]}"

/** `Sab, 9 Jan 2027 · 18.30` (the zone label is a separate field on the Home card). */
internal fun LocalMoment.fullDateTime(words: FormatWords): String =
    "${words.weekdays[weekday]}, ${dayMonthYear(words)} · ${time(words)}"

/** `Jum, 30 Okt · 10.00 WIB`. */
internal fun LocalMoment.shortDateTimeWithZone(
    words: FormatWords,
    zone: TimeZoneLabel,
): String = "${words.weekdays[weekday]}, ${dayMonth(words)} · ${time(words)} ${zone.name}"

/** `Rp 450.000`: whole rupiah with a dot as thousands separator in both languages. */
internal fun formatRupiah(amount: Long): String {
    val digits = amount.coerceAtLeast(0L).toString()
    val grouped =
        digits.reversed().chunked(THOUSAND_DIGITS).joinToString(".").reversed()
    return "Rp $grouped"
}

/**
 * "7 hari 14 jam lagi" style remaining time from [remainingMillis]: days and hours, hours and minutes, or minutes.
 * Under a minute (or already past) gives the "less than a minute" words.
 */
internal fun formatRemaining(
    remainingMillis: Long,
    words: FormatWords,
): String {
    val totalMinutes = remainingMillis / MILLIS_PER_MINUTE
    if (totalMinutes < 1) return words.lessThanMinute
    val days = totalMinutes / MINUTES_PER_DAY_CYCLE
    val hours = totalMinutes / MINUTES_PER_HOUR % HOURS_PER_DAY
    val minutes = totalMinutes % MINUTES_PER_HOUR
    val parts =
        when {
            days > 0 -> listOf(days to words.day, hours to words.hour)
            hours > 0 -> listOf(hours to words.hour, minutes to words.minute)
            else -> listOf(minutes to words.minute)
        }
    val text =
        parts.filter { (count, _) -> count > 0 }.joinToString(" ") { (count, unit) ->
            "$count ${if (count == 1L) unit.first else unit.second}"
        }
    return "$text ${words.remainingSuffix}"
}
