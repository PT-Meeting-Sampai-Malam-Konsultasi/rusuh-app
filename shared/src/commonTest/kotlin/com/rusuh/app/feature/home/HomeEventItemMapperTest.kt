package com.rusuh.app.feature.home

import com.rusuh.app.ui.components.EventStatus
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
}
