package com.rusuh.app.data.clock

private val RFC3339 =
    Regex("""^(\d{4})-(\d{2})-(\d{2})[Tt ](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?([Zz]|[+-]\d{2}:\d{2})$""")

private const val DAYS_FROM_CIVIL_TO_EPOCH = 719_468L
private const val DAYS_PER_ERA = 146_097L
private const val DAYS_PER_YEAR_CYCLE = 365L
private const val YEARS_PER_ERA = 400L
private const val MARCH_BASED_MONTH_SHIFT = 9
private const val MARCH_PHASE = 153
private const val MARCH_PHASE_OFFSET = 2
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_DAY = 86_400L
private const val MILLIS = 1_000L
private const val MILLI_DIGITS = 3
private const val MAX_HOUR = 23
private const val MAX_MINUTE = 59
private const val MAX_SECOND = 60
private const val MAX_MONTH = 12
private const val MAX_DAY = 31

/** Parses RFC 3339 (`2026-11-14T10:40:00Z`, fractions and `+07:00` offsets allowed) to epoch millis, or null. */
internal fun parseRfc3339Millis(text: String): Long? {
    val match = RFC3339.matchEntire(text.trim()) ?: return null
    val g = match.groupValues
    val year = g[1].toInt()
    val month = g[2].toInt()
    val day = g[3].toInt()
    val hour = g[4].toInt()
    val minute = g[5].toInt()
    val second = g[6].toInt()
    val valid =
        month in 1..MAX_MONTH && day in 1..MAX_DAY && hour <= MAX_HOUR && minute <= MAX_MINUTE && second <= MAX_SECOND
    return if (valid) {
        val fraction = g[7].padEnd(MILLI_DIGITS, '0').take(MILLI_DIGITS).toLong()
        val zone = g[8]
        val offsetSeconds =
            if (zone.equals("Z", ignoreCase = true)) {
                0L
            } else {
                val sign = if (zone[0] == '-') -1 else 1
                val hours = zone.substring(1, 3).toLong()
                val minutes = zone.substring(4, 6).toLong()
                sign * (hours * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE)
            }
        val seconds =
            daysFromCivil(year, month, day) * SECONDS_PER_DAY + hour * SECONDS_PER_HOUR +
                minute * SECONDS_PER_MINUTE + second - offsetSeconds
        seconds * MILLIS + fraction
    } else {
        null
    }
}

/** Days since 1970-01-01 of a proleptic Gregorian date (Howard Hinnant's algorithm). */
private fun daysFromCivil(
    year: Int,
    month: Int,
    day: Int,
): Long {
    val y = if (month <= MARCH_PHASE_OFFSET) year - 1L else year.toLong()
    val era = (if (y >= 0) y else y - YEARS_PER_ERA + 1) / YEARS_PER_ERA
    val yearOfEra = y - era * YEARS_PER_ERA
    val shifted = if (month > MARCH_PHASE_OFFSET) month - MARCH_PHASE_OFFSET - 1 else month + MARCH_BASED_MONTH_SHIFT
    val dayOfYear = (MARCH_PHASE * shifted + MARCH_PHASE_OFFSET) / 5 + day - 1
    val dayOfEra = yearOfEra * DAYS_PER_YEAR_CYCLE + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * DAYS_PER_ERA + dayOfEra - DAYS_FROM_CIVIL_TO_EPOCH
}
