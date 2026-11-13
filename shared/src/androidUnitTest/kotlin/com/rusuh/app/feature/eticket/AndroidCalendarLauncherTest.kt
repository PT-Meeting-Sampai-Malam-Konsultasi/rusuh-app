package com.rusuh.app.feature.eticket

import android.content.Intent
import android.provider.CalendarContract
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
class AndroidCalendarLauncherTest {
    private val sampleEvent =
        CalendarEvent(
            title = "SAMUDRA",
            location = "GBK",
            beginMillis = 1_797_066_000_000L,
            endMillis = 1_797_076_800_000L,
            timeZoneId = "Asia/Jakarta",
        )

    @Test
    fun buildCalendarIntentHasActionInsert() {
        val intent = AndroidCalendarLauncher.buildCalendarIntent(sampleEvent)
        assertEquals(Intent.ACTION_INSERT, intent.action)
    }

    @Test
    fun buildCalendarIntentHasCalendarEventsUri() {
        val intent = AndroidCalendarLauncher.buildCalendarIntent(sampleEvent)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
    }

    @Test
    fun buildCalendarIntentHasTitleExtra() {
        val intent = AndroidCalendarLauncher.buildCalendarIntent(sampleEvent)
        assertEquals("SAMUDRA", intent.getStringExtra(CalendarContract.Events.TITLE))
    }

    @Test
    fun buildCalendarIntentHasLocationExtra() {
        val intent = AndroidCalendarLauncher.buildCalendarIntent(sampleEvent)
        assertEquals("GBK", intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
    }

    @Test
    fun buildCalendarIntentHasBeginTimeExtra() {
        val intent = AndroidCalendarLauncher.buildCalendarIntent(sampleEvent)
        assertEquals(1_797_066_000_000L, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
    }

    @Test
    fun buildCalendarIntentHasEndTimeExtra() {
        val intent = AndroidCalendarLauncher.buildCalendarIntent(sampleEvent)
        assertEquals(1_797_076_800_000L, intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0))
    }

    @Test
    fun buildCalendarIntentHasTimeZoneExtra() {
        val intent = AndroidCalendarLauncher.buildCalendarIntent(sampleEvent)
        assertEquals("Asia/Jakarta", intent.getStringExtra(CalendarContract.Events.EVENT_TIMEZONE))
    }

    @Test
    fun buildCalendarIntentWithWitaTimeZone() {
        val event = sampleEvent.copy(timeZoneId = "Asia/Makassar")
        val intent = AndroidCalendarLauncher.buildCalendarIntent(event)
        assertEquals("Asia/Makassar", intent.getStringExtra(CalendarContract.Events.EVENT_TIMEZONE))
    }

    @Test
    fun buildCalendarIntentWithWitTimeZone() {
        val event = sampleEvent.copy(timeZoneId = "Asia/Jayapura")
        val intent = AndroidCalendarLauncher.buildCalendarIntent(event)
        assertEquals("Asia/Jayapura", intent.getStringExtra(CalendarContract.Events.EVENT_TIMEZONE))
    }

    @Test
    fun buildCalendarIntentWithLongEventDetails() {
        val event =
            CalendarEvent(
                title = "SAMUDRA GELOMBANG TUR 2026",
                location = "Stadion Utama Gelora Bung Karno",
                beginMillis = 1_797_066_000_000L,
                endMillis = 1_797_076_800_000L,
                timeZoneId = "Asia/Jakarta",
            )
        val intent = AndroidCalendarLauncher.buildCalendarIntent(event)
        assertEquals("SAMUDRA GELOMBANG TUR 2026", intent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals("Stadion Utama Gelora Bung Karno", intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
    }

    @Test
    fun buildCalendarIntentDataIsNotNull() {
        val intent = AndroidCalendarLauncher.buildCalendarIntent(sampleEvent)
        assertNotNull(intent.data)
    }
}
