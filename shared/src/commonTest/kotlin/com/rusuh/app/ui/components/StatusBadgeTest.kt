package com.rusuh.app.ui.components

import com.rusuh.app.ui.theme.RusuhStatusColors
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.event_card_status_almost_gone
import rusuh_app.shared.generated.resources.event_card_status_ended
import rusuh_app.shared.generated.resources.event_card_status_on_sale
import rusuh_app.shared.generated.resources.status_available
import rusuh_app.shared.generated.resources.status_coming_soon
import rusuh_app.shared.generated.resources.status_sold_out
import rusuh_app.shared.generated.resources.status_temporarily_full
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusBadgeTest {
    @Test
    fun waitingRoomOpenMapsToPinkPalette() {
        assertEquals(Res.string.status_available, EventStatus.WaitingRoomOpen.badgeLabel())
        assertEquals(RusuhStatusColors.waitingRoomOpen, EventStatus.WaitingRoomOpen.badgeColors())
    }

    @Test
    fun onSaleMapsToGreenPalette() {
        assertEquals(Res.string.event_card_status_on_sale, EventStatus.OnSale.badgeLabel())
        assertEquals(RusuhStatusColors.onSale, EventStatus.OnSale.badgeColors())
    }

    @Test
    fun almostGoneMapsToOrangePalette() {
        assertEquals(Res.string.event_card_status_almost_gone, EventStatus.AlmostGone.badgeLabel())
        assertEquals(RusuhStatusColors.almostGone, EventStatus.AlmostGone.badgeColors())
    }

    @Test
    fun temporarilyFullMapsToDashedLimitedPalette() {
        assertEquals(Res.string.status_temporarily_full, EventStatus.TemporarilyFull.badgeLabel())
        assertEquals(RusuhStatusColors.temporarilyFull, EventStatus.TemporarilyFull.badgeColors())
        assertTrue(EventStatus.TemporarilyFull.badgeColors().dashedBorder)
    }

    @Test
    fun soldOutMapsToSoldOutPalette() {
        val status = EventStatus.SoldOut(soldOutInMinutes = 41, capacity = 12_000)
        assertEquals(Res.string.status_sold_out, status.badgeLabel())
        assertEquals(RusuhStatusColors.soldOutStamp, status.badgeColors())
    }

    @Test
    fun comingSoonMapsToUpcomingPalette() {
        val status = EventStatus.ComingSoon(countdown = "7 hari 14 jam lagi")
        assertEquals(Res.string.status_coming_soon, status.badgeLabel())
        assertEquals(RusuhStatusColors.upcomingStamp, status.badgeColors())
    }

    @Test
    fun endedMapsToEndedPalette() {
        val status = EventStatus.Ended(closedOn = "29 Okt")
        assertEquals(Res.string.event_card_status_ended, status.badgeLabel())
        assertEquals(RusuhStatusColors.ended, status.badgeColors())
    }
}
