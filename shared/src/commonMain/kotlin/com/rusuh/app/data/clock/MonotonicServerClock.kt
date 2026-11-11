package com.rusuh.app.data.clock

import com.rusuh.app.domain.clock.ServerClock
import kotlin.time.TimeSource

/**
 * Stub [ServerClock]: server time anchored at [serverEpochMillisAtStart] and advanced by a
 * monotonic source, so it never jumps with device clock changes.
 *
 * TODO(FR-17-M1 #82): replace with a clock synced to the server time returned by the booking API.
 */
class MonotonicServerClock(
    private val serverEpochMillisAtStart: Long = 0L,
) : ServerClock {
    private val mark = TimeSource.Monotonic.markNow()

    override fun nowMillis(): Long = serverEpochMillisAtStart + mark.elapsedNow().inWholeMilliseconds
}
