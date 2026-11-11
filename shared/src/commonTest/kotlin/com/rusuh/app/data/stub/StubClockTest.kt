package com.rusuh.app.data.stub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class StubClockTest {
    @Test
    fun defaultStartIsTheSpecServerNow() {
        assertEquals("2026-11-14T10:40:00Z", StubClock().isoAt())
    }

    @Test
    fun formatsEpochAndLeapDays() {
        assertEquals("1970-01-01T00:00:00Z", StubClock(0L).isoAt())
        assertEquals("2024-02-29T23:59:59Z", StubClock(1_709_251_199_000L).isoAt())
        assertEquals("2024-03-01T00:00:00Z", StubClock(1_709_251_199_000L).isoAt(1))
        assertEquals("1969-12-31T23:59:59Z", StubClock(0L).isoAt(-1))
    }

    @Test
    fun rendersPlaceholdersRelativeToTheClock() {
        val clock = StubClock()
        val template = """{"a":"@{now}","b":"@{now+300}","c":"@{now-60}","d":@{epoch},"e":@{epoch+5}}"""

        assertEquals(
            """{"a":"2026-11-14T10:40:00Z","b":"2026-11-14T10:45:00Z","c":"2026-11-14T10:39:00Z",""" +
                """"d":1794652800,"e":1794652805}""",
            clock.render(template),
        )
    }

    @Test
    fun advancingMovesEveryRenderedTime() {
        val clock = StubClock()

        clock.advanceBy(2.minutes + 30.seconds)

        assertEquals("2026-11-14T10:42:30Z", clock.render("@{now}"))
        assertEquals(StubClock.DEFAULT_START_MILLIS + 150_000, clock.nowMillis())
        clock.setTo(0L)
        assertEquals("1970-01-01T00:00:00Z", clock.render("@{now}"))
    }

    @Test
    fun aFrozenClockNeverMovesOnItsOwn() {
        val clock = StubClock()

        assertEquals(clock.nowMillis(), clock.nowMillis())
        assertEquals(StubClock.DEFAULT_START_MILLIS, clock.nowMillis())
    }

    @Test
    fun aTickingClockStartsAtTheSpecTimeAndRuns() {
        val clock = StubClock.ticking()
        val first = clock.nowMillis()

        var later = first
        while (later == first) later = clock.nowMillis()

        assertTrue(first >= StubClock.DEFAULT_START_MILLIS)
        assertTrue(later > first)
        clock.setTo(0L)
        assertTrue(clock.nowMillis() < StubClock.DEFAULT_START_MILLIS)
    }
}
