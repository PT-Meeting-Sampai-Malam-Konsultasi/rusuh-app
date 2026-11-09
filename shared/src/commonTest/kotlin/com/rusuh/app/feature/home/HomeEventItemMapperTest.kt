package com.rusuh.app.feature.home

import com.rusuh.app.ui.components.EventFormat
import com.rusuh.app.ui.components.EventStatus
import com.rusuh.app.ui.components.buildCardIdentity
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeEventItemMapperTest {
    @Test
    fun mapsTitleOntoCard() {
        val card = HomeEventItem(id = "1", title = "Konser Senja").toEventCardData()
        assertEquals("Konser Senja", card.title)
    }

    @Test
    fun defaultStatusIsOnSaleSoCardsStayClickable() {
        // A sold-out status would disable the card; until M4 supplies a real
        // status the mapper must keep events openable.
        val card = HomeEventItem(id = "2", title = "Festival").toEventCardData()
        assertEquals(EventStatus.OnSale, card.status)
    }

    @Test
    fun placeholderFormatIsUnknownSoNoFormatIsMislabeled() {
        // Until M4 supplies a real format, the card must not claim a concrete one.
        val card = HomeEventItem(id = "3", title = "Tur").toEventCardData()
        assertEquals(EventFormat.UNKNOWN, card.format)
    }

    @Test
    fun mappedCardIdentityIsTitleAloneWithoutEmptySegments() {
        // Blank venue/date/format must not leak empty segments or a lone timezone
        // into the screen-reader description. (The localized status suffix is
        // appended by the @Composable buildCardDescription at render time.)
        val card = HomeEventItem(id = "4", title = "Konser Senja").toEventCardData()
        assertEquals("Konser Senja", buildCardIdentity(card))
    }

    @Test
    fun mapsEveryDisplayFieldOntoCard() {
        val status = EventStatus.SoldOut(soldOutInMinutes = 41, capacity = 12_000)
        val card =
            HomeEventItem(
                id = "5",
                title = "Deru",
                subtitle = "Malam Mesin",
                format = EventFormat.LIVE,
                venue = "Jatim Expo",
                city = "Surabaya",
                dateTime = "Jum, 4 Des 2026 · 20.00",
                status = status,
                priceFrom = "Rp 1",
            ).toEventCardData()
        assertEquals("Malam Mesin", card.subtitle)
        assertEquals(EventFormat.LIVE, card.format)
        assertEquals("Jatim Expo", card.venue)
        assertEquals("Surabaya", card.city)
        assertEquals("Jum, 4 Des 2026 · 20.00", card.dateTime)
        assertEquals(status, card.status)
        assertEquals("Rp 1", card.priceFrom)
    }

    @Test
    fun previewEventsCoverEveryFigmaStatus() {
        val statuses = homePreviewEvents().map { it.status }
        assertEquals(7, statuses.size)
        assertEquals(
            setOf(
                EventStatus.WaitingRoomOpen::class,
                EventStatus.OnSale::class,
                EventStatus.AlmostGone::class,
                EventStatus.TemporarilyFull::class,
                EventStatus.SoldOut::class,
                EventStatus.ComingSoon::class,
                EventStatus.Ended::class,
            ),
            statuses.map { it::class }.toSet(),
        )
    }
}
