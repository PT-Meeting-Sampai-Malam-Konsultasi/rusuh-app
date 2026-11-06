package com.rusuh.app.ui.components

import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.event_footer_sold_out
import rusuh_app.shared.generated.resources.event_footer_temporarily_full
import rusuh_app.shared.generated.resources.status_passthrough
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
        assertNull(EventStatus.Available.footerSpec())
    }

    @Test
    fun temporarilyFullFooterUsesTheTenMinuteKey() {
        // The "10 minutes" copy now lives in the resource value; the spec maps to that key.
        assertEquals(
            FooterSpec(Res.string.event_footer_temporarily_full),
            EventStatus.TemporarilyFull.footerSpec(),
        )
    }

    @Test
    fun soldOutFooterCarriesCapacityAndMinutesArgs() {
        val spec = EventStatus.SoldOut(soldOutInMinutes = 41, capacity = 12_000).footerSpec()
        assertEquals(FooterSpec(Res.string.event_footer_sold_out, listOf("12.000", 41)), spec)
    }

    @Test
    fun comingSoonFooterPassesTheCountdownThrough() {
        val countdown = "7 hari 14 jam lagi"
        assertEquals(
            FooterSpec(Res.string.status_passthrough, listOf(countdown)),
            EventStatus.ComingSoon(countdown).footerSpec(),
        )
    }

    @Test
    fun thousandsSeparatorUsesDots() {
        assertEquals("12.000", 12_000.formatThousands())
        assertEquals("1.234.567", 1_234_567.formatThousands())
        assertEquals("999", 999.formatThousands())
        assertEquals("0", 0.formatThousands())
    }

    @Test
    fun cardIdentityIncludesTitleTimezoneAndFields() {
        val identity = buildCardIdentity(sampleData(EventStatus.SoldOut(41, 12_000)))
        assertTrue(identity.contains("Langit Sore"), identity)
        assertTrue(identity.contains("WIB"), identity)
    }

    @Test
    fun unknownFormatOmitsFormatFromIdentityWithoutDoubleSeparator() {
        // Use a subtitle that does not itself contain a format word, so the
        // assertions isolate the format segment.
        val data =
            sampleData(EventStatus.Available)
                .copy(subtitle = "Senja Panjang", format = EventFormat.UNKNOWN)
        val identity = buildCardIdentity(data)
        // No stray ", ," where the format label would have been.
        assertTrue(!identity.contains(", ,"), identity)
        // Identity still present; format segment gone.
        assertEquals(
            "Langit Sore, Senja Panjang, Stadion Kridosono Yogyakarta, " +
                "Sab, 9 Jan 2027 · 18.30 WIB",
            identity,
        )
    }

    @Test
    fun knownFormatStillAppearsInIdentity() {
        val data = sampleData(EventStatus.Available).copy(subtitle = "Senja Panjang")
        val identity = buildCardIdentity(data)
        assertTrue(identity.contains("Live"), identity)
    }
}
