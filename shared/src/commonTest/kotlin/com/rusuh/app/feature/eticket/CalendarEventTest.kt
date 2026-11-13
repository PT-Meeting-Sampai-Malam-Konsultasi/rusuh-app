package com.rusuh.app.feature.eticket

import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarEventTest {
    private val threeHoursMillis = 3L * 60 * 60 * 1000

    @Test
    fun toCalendarEventBuildsCorrectTitleAndLocation() {
        val order = sampleETicketOrder(1)
        val event = order.toCalendarEvent()
        assertEquals("SAMUDRA", event.title)
        assertEquals("GBK", event.location)
    }

    @Test
    fun toCalendarEventUsesDoorsOpenAsBeginTime() {
        val order = sampleETicketOrder(1)
        val event = order.toCalendarEvent()
        assertEquals(order.doorsOpenEpochMillis, event.beginMillis)
    }

    @Test
    fun toCalendarEventSetsEndTimeThreeHoursAfterBegin() {
        val order = sampleETicketOrder(1)
        val event = order.toCalendarEvent()
        assertEquals(order.doorsOpenEpochMillis + threeHoursMillis, event.endMillis)
    }

    @Test
    fun wibZoneMapsToAsiaJakarta() {
        assertEquals("Asia/Jakarta", EventZone.Wib.toTimeZoneId())
        val order = sampleETicketOrder(1).copy(zone = EventZone.Wib)
        assertEquals("Asia/Jakarta", order.toCalendarEvent().timeZoneId)
    }

    @Test
    fun witaZoneMapsToAsiaMakassar() {
        assertEquals("Asia/Makassar", EventZone.Wita.toTimeZoneId())
        val order = sampleETicketOrder(1).copy(zone = EventZone.Wita)
        assertEquals("Asia/Makassar", order.toCalendarEvent().timeZoneId)
    }

    @Test
    fun witZoneMapsToAsiaJayapura() {
        assertEquals("Asia/Jayapura", EventZone.Wit.toTimeZoneId())
        val order = sampleETicketOrder(1).copy(zone = EventZone.Wit)
        assertEquals("Asia/Jayapura", order.toCalendarEvent().timeZoneId)
    }

    @Test
    fun midnightDoorsOpenHandledCorrectly() {
        // Midnight WIB on 2026-12-13 = 17:00 UTC on 2026-12-12
        val midnightWib = 1_797_094_800_000L
        val order = sampleETicketOrder(1).copy(doorsOpenEpochMillis = midnightWib)
        val event = order.toCalendarEvent()
        assertEquals(midnightWib, event.beginMillis)
        assertEquals(midnightWib + threeHoursMillis, event.endMillis)
    }

    @Test
    fun eventWithMissingDoorsOpenTimeUsesZero() {
        // Edge case: if doors open is 0 (epoch), should still work
        val order = sampleETicketOrder(1).copy(doorsOpenEpochMillis = 0L)
        val event = order.toCalendarEvent()
        assertEquals(0L, event.beginMillis)
        assertEquals(threeHoursMillis, event.endMillis)
    }

    @Test
    fun longEventNameAndVenueArePreserved() {
        val order = sampleETicketOrder(1, long = true)
        val event = order.toCalendarEvent()
        assertEquals("SAMUDRA GELOMBANG TUR 2026", event.title)
        assertEquals("Stadion Utama Gelora Bung Karno", event.location)
    }
}
