package com.rusuh.app.ui.components

import com.rusuh.app.ui.theme.RusuhStatusColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QueueListItemTest {
    private fun item(
        phase: QueueListPhase,
        position: Int = 10,
    ) = QueueListItemUi(
        eventId = "evt-1",
        title = "Samudra",
        dateVenue = "12 Des 2026 · Jakarta",
        position = position,
        nowServing = "Melayani ke-5",
        phase = phase,
        updatedAgo = "diperbarui 3 detik lalu",
    )

    @Test
    fun phaseLabelsAreTheIndonesianCopy() {
        assertEquals("Antrian dibuka", QueueListPhase.QueueOpen.phaseLabel())
        assertEquals("Berlangsung", QueueListPhase.Live.phaseLabel())
        assertEquals("Dijeda", QueueListPhase.Paused.phaseLabel())
        assertEquals("Giliran Anda", QueueListPhase.YourTurn.phaseLabel())
        assertEquals("Ditutup", QueueListPhase.Closed.phaseLabel())
    }

    @Test
    fun phaseColorsMapToStatusPalette() {
        assertEquals(RusuhStatusColors.upcoming, QueueListPhase.QueueOpen.phaseColors())
        assertEquals(RusuhStatusColors.available, QueueListPhase.Live.phaseColors())
        assertEquals(RusuhStatusColors.limited, QueueListPhase.Paused.phaseColors())
        assertEquals(RusuhStatusColors.available, QueueListPhase.YourTurn.phaseColors())
        assertEquals(RusuhStatusColors.soldOut, QueueListPhase.Closed.phaseColors())
    }

    @Test
    fun descriptionIncludesPositionForActivePhases() {
        val desc = buildQueueItemDescription(item(QueueListPhase.Live, position = 42))
        assertTrue(desc.contains("Samudra"))
        assertTrue(desc.contains("Anda antrian ke-42"))
        assertTrue(desc.contains("Berlangsung"))
    }

    @Test
    fun descriptionOmitsPositionWhenClosed() {
        val desc = buildQueueItemDescription(item(QueueListPhase.Closed))
        assertTrue(desc.contains("Antrian ditutup"))
        assertTrue(!desc.contains("Anda antrian ke-"))
    }

    @Test
    fun positionMustBeAtLeastOne() {
        assertFailsWith<IllegalArgumentException> { item(QueueListPhase.Live, position = 0) }
    }

    @Test
    fun eventIdMustNotBeBlank() {
        assertFailsWith<IllegalArgumentException> {
            QueueListItemUi(
                eventId = " ",
                title = "X",
                dateVenue = "y",
                position = 1,
                nowServing = null,
                phase = QueueListPhase.Live,
                updatedAgo = "z",
            )
        }
    }
}
