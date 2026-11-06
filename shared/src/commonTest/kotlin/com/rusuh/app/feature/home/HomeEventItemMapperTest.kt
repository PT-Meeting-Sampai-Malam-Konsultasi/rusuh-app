package com.rusuh.app.feature.home

import com.rusuh.app.ui.components.EventFormat
import com.rusuh.app.ui.components.EventStatus
import com.rusuh.app.ui.components.buildCardDescription
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeEventItemMapperTest {
    @Test
    fun mapsTitleOntoCard() {
        val card = HomeEventItem(id = "1", title = "Konser Senja").toEventCardData()
        assertEquals("Konser Senja", card.title)
    }

    @Test
    fun placeholderStatusIsAvailableSoCardsStayClickable() {
        // A sold-out status would disable the card; until M4 supplies a real
        // status the mapper must keep events openable.
        val card = HomeEventItem(id = "2", title = "Festival").toEventCardData()
        assertEquals(EventStatus.Available, card.status)
    }

    @Test
    fun placeholderFormatIsUnknownSoNoFormatIsMislabeled() {
        // Until M4 supplies a real format, the card must not claim a concrete one.
        val card = HomeEventItem(id = "3", title = "Tur").toEventCardData()
        assertEquals(EventFormat.UNKNOWN, card.format)
    }

    @Test
    fun mappedCardDescriptionIsTitlePlusAvailableWithoutEmptySegments() {
        // Blank venue/date/format must not leak empty segments or a lone timezone
        // into the screen-reader description.
        val card = HomeEventItem(id = "4", title = "Konser Senja").toEventCardData()
        val desc = buildCardDescription(card)
        assertEquals("Konser Senja. Status: Antrian dibuka", desc)
    }
}
