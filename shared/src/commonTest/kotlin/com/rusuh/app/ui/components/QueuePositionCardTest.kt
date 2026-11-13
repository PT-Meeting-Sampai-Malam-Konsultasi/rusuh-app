package com.rusuh.app.ui.components

import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhTextStyles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QueuePositionCardTest {
    @Test
    fun formatQueueNumberGroupsThousandsWithDots() {
        assertEquals("1", formatQueueNumber(1))
        assertEquals("310", formatQueueNumber(310))
        assertEquals("1.248", formatQueueNumber(1248))
        assertEquals("19.700", formatQueueNumber(19_700))
        assertEquals("1.234.567", formatQueueNumber(1_234_567))
    }

    @Test
    fun formatCountdownIsMinutesAndPaddedSeconds() {
        assertEquals("8:42", formatCountdown(522))
        assertEquals("0:52", formatCountdown(52))
        assertEquals("1:00", formatCountdown(60))
        assertEquals("0:00", formatCountdown(-5))
    }

    @Test
    fun progressPositionIsSeqMinusServed() {
        val progress = QueueProgressUi(servedAtJoin = 17_400, nowServing = 18_452, myNumber = 19_700)
        assertEquals(1248, progress.position)
    }

    @Test
    fun progressFractionIsServedShareOfSpan() {
        assertEquals(0.5f, QueueProgressUi(servedAtJoin = 0, nowServing = 50, myNumber = 100).fraction)
        assertEquals(0f, QueueProgressUi(servedAtJoin = 10, nowServing = 10, myNumber = 100).fraction)
        assertEquals(1f, QueueProgressUi(servedAtJoin = 10, nowServing = 100, myNumber = 100).fraction)
        assertEquals(0f, QueueProgressUi(servedAtJoin = 100, nowServing = 100, myNumber = 100).fraction)
    }

    @Test
    fun progressPositionNeverDropsBelowOne() {
        assertEquals(1, QueueProgressUi(servedAtJoin = 0, nowServing = 100, myNumber = 100).position)
    }

    @Test
    fun progressRejectsInconsistentNumbers() {
        assertFailsWith<IllegalArgumentException> {
            QueueProgressUi(
                servedAtJoin = 200,
                nowServing = 200,
                myNumber = 100,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            QueueProgressUi(
                servedAtJoin = 50,
                nowServing = 40,
                myNumber = 100,
            )
        }
    }

    @Test
    fun positionMustBeAtLeastOne() {
        assertFailsWith<IllegalArgumentException> { QueuePositionUi(position = 0) }
    }

    @Test
    fun etaMustBeNonNegative() {
        assertFailsWith<IllegalArgumentException> { QueuePositionUi(position = 1, estimatedWaitMinutes = -1) }
    }

    @Test
    fun faceColorPerPhase() {
        assertEquals(RusuhColors.Pink, QueuePhase.Waiting.faceColor())
        assertEquals(RusuhColors.Ink, QueuePhase.Paused.faceColor())
    }

    @Test
    fun longNumbersUseCompactStyle() {
        assertEquals(RusuhTextStyles.queueNumber, queueNumberStyle("1.248"))
        assertEquals(RusuhTextStyles.queueNumberCompact, queueNumberStyle("19.700"))
    }
}
