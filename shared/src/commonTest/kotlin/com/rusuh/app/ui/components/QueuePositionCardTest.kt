package com.rusuh.app.ui.components

import com.rusuh.app.ui.theme.RusuhStatusColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QueuePositionCardTest {
    private fun data(
        position: Int = 42,
        peopleAhead: Int = 41,
        phase: QueuePhase = QueuePhase.Waiting,
        eta: String? = "~8 menit",
    ) = QueuePositionUi(position = position, peopleAhead = peopleAhead, phase = phase, estimatedWait = eta)

    @Test
    fun accentColorsMapPerPhase() {
        assertEquals(RusuhStatusColors.upcoming, QueuePhase.Waiting.accentColors())
        assertEquals(RusuhStatusColors.available, QueuePhase.YourTurn.accentColors())
        assertEquals(RusuhStatusColors.soldOut, QueuePhase.QueueClosed.accentColors())
    }

    @Test
    fun positionLabelPerPhase() {
        assertEquals("Anda antrian ke-", QueuePhase.Waiting.positionLabel())
        assertTrue(QueuePhase.YourTurn.positionLabel().contains("Giliran Anda"))
        assertTrue(QueuePhase.QueueClosed.positionLabel().contains("ditutup"))
    }

    @Test
    fun peopleAheadLabelCountsWhileWaiting() {
        assertEquals("41 orang di depan Anda", peopleAheadLabel(data(peopleAhead = 41)))
    }

    @Test
    fun peopleAheadLabelZeroWhileWaiting() {
        assertEquals("Tidak ada antrian di depan Anda", peopleAheadLabel(data(peopleAhead = 0)))
    }

    @Test
    fun peopleAheadLabelYourTurnIgnoresCount() {
        val label = peopleAheadLabel(data(peopleAhead = 5, phase = QueuePhase.YourTurn))
        assertEquals("Tidak ada antrian di depan Anda", label)
    }

    @Test
    fun peopleAheadLabelClosedShowsSoldOut() {
        val label = peopleAheadLabel(data(phase = QueuePhase.QueueClosed))
        assertEquals("Tiket telah habis terjual", label)
    }

    @Test
    fun descriptionIncludesPositionPhaseAndEta() {
        val desc = buildQueueDescription(data(position = 42, peopleAhead = 41, eta = "~8 menit"))
        assertTrue(desc.contains("Anda antrian ke-42"), desc)
        assertTrue(desc.contains("41 orang di depan Anda"), desc)
        assertTrue(desc.contains("Estimasi tunggu ~8 menit"), desc)
    }

    @Test
    fun descriptionOmitsEtaWhenNull() {
        val desc = buildQueueDescription(data(eta = null))
        assertTrue(!desc.contains("Estimasi"), desc)
    }

    @Test
    fun positionMustBeAtLeastOne() {
        assertFailsWith<IllegalArgumentException> { data(position = 0) }
    }

    @Test
    fun peopleAheadMustBeNonNegative() {
        assertFailsWith<IllegalArgumentException> { data(peopleAhead = -1) }
    }

    @Test
    fun peopleAheadCannotExceedPositionMinusOne() {
        assertFailsWith<IllegalArgumentException> { data(position = 3, peopleAhead = 3) }
    }
}
