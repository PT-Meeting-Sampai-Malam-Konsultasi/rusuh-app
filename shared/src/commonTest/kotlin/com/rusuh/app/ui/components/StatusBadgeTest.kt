package com.rusuh.app.ui.components

import com.rusuh.app.ui.theme.RusuhStatusColors
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.status_available
import rusuh_app.shared.generated.resources.status_coming_soon
import rusuh_app.shared.generated.resources.status_sold_out
import rusuh_app.shared.generated.resources.status_temporarily_full
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusBadgeTest {
    @Test
    fun availableMapsToGreenPalette() {
        assertEquals(Res.string.status_available, EventStatus.Available.badgeLabel())
        assertEquals(RusuhStatusColors.available, EventStatus.Available.badgeColors())
    }

    @Test
    fun temporarilyFullMapsToLimitedPalette() {
        assertEquals(Res.string.status_temporarily_full, EventStatus.TemporarilyFull.badgeLabel())
        assertEquals(RusuhStatusColors.limited, EventStatus.TemporarilyFull.badgeColors())
    }

    @Test
    fun soldOutMapsToSoldOutPalette() {
        val status = EventStatus.SoldOut(soldOutInMinutes = 41, capacity = 12_000)
        assertEquals(Res.string.status_sold_out, status.badgeLabel())
        assertEquals(RusuhStatusColors.soldOut, status.badgeColors())
    }

    @Test
    fun comingSoonMapsToUpcomingPalette() {
        val status = EventStatus.ComingSoon(countdown = "7 hari 14 jam lagi")
        assertEquals(Res.string.status_coming_soon, status.badgeLabel())
        assertEquals(RusuhStatusColors.upcoming, status.badgeColors())
    }
}
