package com.rusuh.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventCardTest {
    private fun sampleData(status: EventStatus) =
        EventCardData(
            title = "Langit Sore",
            subtitle = "Senja Panjang Live",
            format = EventFormat.LIVE,
            venue = "Stadion Kridosono",
            city = "Yogyakarta",
            dateTime = "Sab, 9 Jan 2027 · 18.30",
            timeZone = TimeZoneLabel.WIB,
            status = status,
            priceFrom = "Mulai Rp 325.000",
        )

    @Test
    fun availableHasNoFooter() {
        assertNull(EventStatus.Available.footerText())
    }

    @Test
    fun temporarilyFullFooterMentionsTenMinutes() {
        val footer = EventStatus.TemporarilyFull.footerText()
        assertTrue(footer != null && footer.contains("10 menit"), "got: $footer")
    }

    @Test
    fun soldOutFooterIncludesCapacityAndMinutes() {
        val footer = EventStatus.SoldOut(soldOutInMinutes = 41, capacity = 12_000).footerText()
        assertEquals("Semua 12.000 tiket terjual dalam 41 menit.", footer)
    }

    @Test
    fun comingSoonFooterIsTheCountdown() {
        val countdown = "7 hari 14 jam lagi"
        assertEquals(countdown, EventStatus.ComingSoon(countdown).footerText())
    }

    @Test
    fun thousandsSeparatorUsesDots() {
        assertEquals("12.000", 12_000.formatThousands())
        assertEquals("1.234.567", 1_234_567.formatThousands())
        assertEquals("999", 999.formatThousands())
        assertEquals("0", 0.formatThousands())
    }

    @Test
    fun cardDescriptionIncludesIdentityStatusAndFooter() {
        val desc = buildCardDescription(sampleData(EventStatus.SoldOut(41, 12_000)))
        assertTrue(desc.contains("Langit Sore"), desc)
        assertTrue(desc.contains("WIB"), desc)
        assertTrue(desc.contains("Habis terjual"), desc)
        assertTrue(desc.contains("12.000 tiket terjual"), desc)
    }

    @Test
    fun cardDescriptionOmitsFooterWhenAvailable() {
        val desc = buildCardDescription(sampleData(EventStatus.Available))
        assertTrue(desc.endsWith("Status: Antrian dibuka"), desc)
    }
}
