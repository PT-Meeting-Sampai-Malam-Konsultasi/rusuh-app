package com.rusuh.app.ui.components

import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.event_card_action_buy
import rusuh_app.shared.generated.resources.event_card_action_join_queue
import rusuh_app.shared.generated.resources.event_card_action_queue_anyway
import rusuh_app.shared.generated.resources.event_card_action_remind
import rusuh_app.shared.generated.resources.event_card_footer_ended
import rusuh_app.shared.generated.resources.event_card_footer_opens
import rusuh_app.shared.generated.resources.event_footer_sold_out
import rusuh_app.shared.generated.resources.event_footer_temporarily_full
import rusuh_app.shared.generated.resources.status_passthrough
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            priceFrom = "Rp 325.000",
        )

    @Test
    fun purchasableStatusesHaveNoFooter() {
        assertEquals(emptyList(), EventStatus.OnSale.footerSpecs())
        assertEquals(emptyList(), EventStatus.WaitingRoomOpen.footerSpecs())
        assertEquals(emptyList(), EventStatus.AlmostGone.footerSpecs())
    }

    @Test
    fun temporarilyFullFooterUsesTheTenMinuteKey() {
        // The "10 minutes" copy now lives in the resource value; the spec maps to that key.
        assertEquals(
            listOf(FooterSpec(Res.string.event_footer_temporarily_full)),
            EventStatus.TemporarilyFull.footerSpecs(),
        )
    }

    @Test
    fun soldOutFooterCarriesCapacityAndMinutesArgs() {
        val spec = EventStatus.SoldOut(soldOutInMinutes = 41, capacity = 12_000).footerSpecs()
        assertEquals(listOf(FooterSpec(Res.string.event_footer_sold_out, listOf("12.000", 41))), spec)
    }

    @Test
    fun comingSoonFooterPassesTheCountdownThrough() {
        val countdown = "7 hari 14 jam lagi"
        assertEquals(
            listOf(FooterSpec(Res.string.status_passthrough, listOf(countdown))),
            EventStatus.ComingSoon(countdown).footerSpecs(),
        )
    }

    @Test
    fun comingSoonWithOpensAtAddsOpensLineBeforeCountdown() {
        val status = EventStatus.ComingSoon(countdown = "7 hari 14 jam lagi", opensAt = "Jum, 30 Okt · 10.00 WIB")
        assertEquals(
            listOf(
                FooterSpec(Res.string.event_card_footer_opens, listOf("Jum, 30 Okt · 10.00 WIB")),
                FooterSpec(Res.string.status_passthrough, listOf("7 hari 14 jam lagi")),
            ),
            status.footerSpecs(),
        )
    }

    @Test
    fun endedFooterCarriesTheCloseDate() {
        assertEquals(
            listOf(FooterSpec(Res.string.event_card_footer_ended, listOf("29 Okt"))),
            EventStatus.Ended("29 Okt").footerSpecs(),
        )
    }

    @Test
    fun soldOutAndEndedAreDisabledOthersAreNot() {
        assertTrue(EventStatus.SoldOut(41, 12_000).isDisabled)
        assertTrue(EventStatus.Ended("29 Okt").isDisabled)
        assertFalse(EventStatus.OnSale.isDisabled)
        assertFalse(EventStatus.TemporarilyFull.isDisabled)
        assertFalse(EventStatus.ComingSoon("1 jam lagi").isDisabled)
    }

    @Test
    fun actionsMapPerStatus() {
        assertEquals(
            CardAction(Res.string.event_card_action_join_queue, primary = true),
            EventStatus.WaitingRoomOpen.cardAction(),
        )
        assertEquals(CardAction(Res.string.event_card_action_buy, primary = true), EventStatus.OnSale.cardAction())
        assertEquals(CardAction(Res.string.event_card_action_buy, primary = true), EventStatus.AlmostGone.cardAction())
        assertEquals(
            CardAction(Res.string.event_card_action_queue_anyway, primary = false),
            EventStatus.TemporarilyFull.cardAction(),
        )
        assertEquals(
            CardAction(Res.string.event_card_action_remind, primary = false),
            EventStatus.ComingSoon("1 jam lagi").cardAction(),
        )
        assertNull(EventStatus.SoldOut(41, 12_000).cardAction())
        assertNull(EventStatus.Ended("29 Okt").cardAction())
    }

    @Test
    fun scheduleTextPutsDateTimeAndZoneThenVenueOnSeparateLines() {
        assertEquals(
            "Sab, 9 Jan 2027 · 18.30 WIB\nStadion Kridosono, Yogyakarta",
            scheduleText(sampleData(EventStatus.OnSale)),
        )
    }

    @Test
    fun scheduleTextSkipsBlankParts() {
        val data = sampleData(EventStatus.OnSale).copy(dateTime = "", venue = "", city = "")
        assertEquals("", scheduleText(data))
    }

    @Test
    fun everyPosterToneHasADistinctBackground() {
        val backgrounds = EventPosterTone.entries.map { it.palette().background }
        assertEquals(backgrounds.size, backgrounds.toSet().size)
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
            sampleData(EventStatus.OnSale)
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
        val data = sampleData(EventStatus.OnSale).copy(subtitle = "Senja Panjang")
        val identity = buildCardIdentity(data)
        assertTrue(identity.contains("Live"), identity)
    }
}
