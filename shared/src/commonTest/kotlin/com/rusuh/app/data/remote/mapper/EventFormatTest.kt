package com.rusuh.app.data.remote.mapper

import com.rusuh.app.data.clock.parseRfc3339Millis
import com.rusuh.app.data.network.AppLanguage
import com.rusuh.app.ui.components.TimeZoneLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class EventFormatTest {
    private fun moment(
        rfc3339: String,
        zone: TimeZoneLabel = TimeZoneLabel.WIB,
    ) = localMoment(parseRfc3339Millis(rfc3339)!!, zone)

    private val id = AppLanguage.Indonesian.words()
    private val en = AppLanguage.English.words()

    @Test
    fun theWallClockFollowsTheZoneAndCrossesDayMonthAndYear() {
        assertEquals(
            LocalMoment(2027, 1, 1, 7, 0, weekday = 5),
            moment("2027-01-01T00:00:00Z"),
        )
        assertEquals(
            LocalMoment(2026, 12, 31, 23, 30, weekday = 4),
            moment("2026-12-31T16:30:00Z"),
        )
        assertEquals(
            LocalMoment(2027, 1, 1, 0, 30, weekday = 5),
            moment("2026-12-31T17:30:00Z"),
        )
    }

    @Test
    fun leapDaysAreRespected() {
        assertEquals(LocalMoment(2028, 2, 29, 10, 0, weekday = 2), moment("2028-02-29T03:00:00Z"))
        assertEquals(LocalMoment(2028, 3, 1, 0, 30, weekday = 3), moment("2028-02-29T17:30:00Z"))
        assertEquals(LocalMoment(2027, 3, 1, 0, 0, weekday = 1), moment("2027-02-28T17:00:00Z"))
    }

    @Test
    fun theEpochAndDatesBeforeItWork() {
        assertEquals(LocalMoment(1970, 1, 1, 7, 0, weekday = 4), moment("1970-01-01T00:00:00Z"))
        assertEquals(LocalMoment(1969, 12, 31, 23, 59, weekday = 3), moment("1969-12-31T16:59:00Z"))
    }

    @Test
    fun zoneOffsetsAreSevenEightAndNineHours() {
        val utc = parseRfc3339Millis("2026-12-12T12:00:00Z")!!
        assertEquals(19, localMoment(utc, TimeZoneLabel.WIB).hour)
        assertEquals(20, localMoment(utc, TimeZoneLabel.WITA).hour)
        assertEquals(21, localMoment(utc, TimeZoneLabel.WIT).hour)
    }

    @Test
    fun ianaZonesMapToTheIndonesianLabels() {
        assertEquals(TimeZoneLabel.WIB, zoneLabelFor("Asia/Jakarta"))
        assertEquals(TimeZoneLabel.WITA, zoneLabelFor("Asia/Makassar"))
        assertEquals(TimeZoneLabel.WIT, zoneLabelFor("Asia/Jayapura"))
        assertEquals(TimeZoneLabel.WIB, zoneLabelFor(""))
        assertEquals(TimeZoneLabel.WIB, zoneLabelFor("Europe/Paris"))
    }

    @Test
    fun dateLinesUseTheLanguageTables() {
        val m = moment("2026-12-12T12:00:00Z")
        assertEquals("Sab, 12 Des 2026 · 19.00", m.fullDateTime(id))
        assertEquals("Sat, 12 Dec 2026 · 19:00", m.fullDateTime(en))
        assertEquals("12 Des", m.dayMonth(id))
        assertEquals("12 Des 2026", m.dayMonthYear(id))
        assertEquals("Sab, 12 Des · 19.00 WIB", m.shortDateTimeWithZone(id, TimeZoneLabel.WIB))
        assertEquals("Mei", moment("2026-05-05T05:00:00Z").dayMonth(id).substringAfter(' '))
        assertEquals("May", moment("2026-05-05T05:00:00Z").dayMonth(en).substringAfter(' '))
        assertEquals("09.05", moment("2026-05-05T02:05:00Z").time(id))
    }

    @Test
    fun rupiahUsesDotThousands() {
        assertEquals("Rp 0", formatRupiah(0))
        assertEquals("Rp 999", formatRupiah(999))
        assertEquals("Rp 1.000", formatRupiah(1_000))
        assertEquals("Rp 450.000", formatRupiah(450_000))
        assertEquals("Rp 1.250.000", formatRupiah(1_250_000))
    }

    @Test
    fun remainingTimePicksTheTwoLargestUnits() {
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour
        assertEquals("7 hari 14 jam lagi", formatRemaining(7 * day + 14 * hour + 5 * minute, id))
        assertEquals("1 day 1 hour left", formatRemaining(day + hour, en))
        assertEquals("2 days left", formatRemaining(2 * day, en))
        assertEquals("3 jam 20 menit lagi", formatRemaining(3 * hour + 20 * minute, id))
        assertEquals("5 jam lagi", formatRemaining(5 * hour, id))
        assertEquals("45 menit lagi", formatRemaining(45 * minute + 59_000, id))
        assertEquals("1 min left", formatRemaining(minute, en))
    }

    @Test
    fun underAMinuteOrAlreadyPastIsLessThanAMinute() {
        assertEquals("kurang dari 1 menit lagi", formatRemaining(59_999, id))
        assertEquals("kurang dari 1 menit lagi", formatRemaining(-5_000, id))
        assertEquals("less than 1 min left", formatRemaining(0, en))
    }
}
