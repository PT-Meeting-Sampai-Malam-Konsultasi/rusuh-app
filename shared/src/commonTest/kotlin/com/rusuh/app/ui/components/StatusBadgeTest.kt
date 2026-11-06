package com.rusuh.app.ui.components

import com.rusuh.app.ui.theme.RusuhStatusColors
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusBadgeTest {
    @Test
    fun availableMapsToGreenPalette() {
        assertEquals("Antrian dibuka", EventStatus.Available.badgeLabel())
        assertEquals(RusuhStatusColors.available, EventStatus.Available.badgeColors())
    }

    @Test
    fun temporarilyFullMapsToLimitedPalette() {
        assertEquals("Penuh sementara", EventStatus.TemporarilyFull.badgeLabel())
        assertEquals(RusuhStatusColors.limited, EventStatus.TemporarilyFull.badgeColors())
    }

    @Test
    fun soldOutMapsToSoldOutPalette() {
        val status = EventStatus.SoldOut(soldOutInMinutes = 41, capacity = 12_000)
        assertEquals("Habis terjual", status.badgeLabel())
        assertEquals(RusuhStatusColors.soldOut, status.badgeColors())
    }

    @Test
    fun comingSoonMapsToUpcomingPalette() {
        val status = EventStatus.ComingSoon(countdown = "7 hari 14 jam lagi")
        assertEquals("Segera dijual", status.badgeLabel())
        assertEquals(RusuhStatusColors.upcoming, status.badgeColors())
    }
}
