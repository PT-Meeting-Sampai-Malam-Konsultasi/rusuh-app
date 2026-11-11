package com.rusuh.app.data.repository

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueuePacingTest {
    @Test
    fun theBaseIntervalFollowsTheDistanceBands() {
        assertEquals(10_000L, QueuePacing.baseIntervalMillis(300_000))
        assertEquals(10_000L, QueuePacing.baseIntervalMillis(10_001))
        assertEquals(5_000L, QueuePacing.baseIntervalMillis(10_000))
        assertEquals(5_000L, QueuePacing.baseIntervalMillis(1_001))
        assertEquals(2_000L, QueuePacing.baseIntervalMillis(1_000))
        assertEquals(2_000L, QueuePacing.baseIntervalMillis(1))
        assertEquals(5_000L, QueuePacing.baseIntervalMillis(null))
    }

    @Test
    fun jitterStaysWithinTwentyPercentAndActuallyVaries() {
        val pacing = QueuePacing(random = Random(42))
        val waits = List(500) { pacing.jittered(10_000) }

        assertTrue(waits.all { it in 8_000L..12_000L }, "out of range: ${waits.min()}..${waits.max()}")
        assertTrue(waits.min() < 8_400L && waits.max() > 11_600L, "should use the whole band")
    }

    @Test
    fun theSameSeedGivesTheSameWaits() {
        val a = QueuePacing(random = Random(1))
        val b = QueuePacing(random = Random(1))

        assertEquals(List(5) { a.pollDelayMillis(500) }, List(5) { b.pollDelayMillis(500) })
    }

    @Test
    fun aFixedRandomGivesTheExtremes() {
        val low =
            object : Random() {
                override fun nextBits(bitCount: Int) = 0
            }
        assertEquals(1_600L, QueuePacing(random = low).pollDelayMillis(1))
        val high =
            object : Random() {
                override fun nextBits(bitCount: Int) = -1 ushr (32 - bitCount)
            }
        assertTrue(QueuePacing(random = high).pollDelayMillis(1) in 2_399L..2_400L)
    }

    @Test
    fun theRandomWaitStaysBelowItsMaximum() {
        val pacing = QueuePacing(random = Random(3))

        assertTrue(List(200) { pacing.randomUpTo(30_000) }.all { it in 0L..30_000L })
    }
}
