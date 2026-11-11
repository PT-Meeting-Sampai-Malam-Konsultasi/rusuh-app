package com.rusuh.app.data.clock

import com.rusuh.app.domain.clock.ServerClock
import kotlin.concurrent.Volatile
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Receives the server time seen in a response. */
fun interface ServerTimeSink {
    fun recordServerNow(serverEpochMillis: Long)
}

/**
 * The app's [ServerClock]: server time = the last recorded `serverNow` plus the monotonic time elapsed since it was
 * recorded, i.e. a stored `serverNow - monotonicNow` offset. It never jumps with device clock changes. Every
 * response carrying `serverNow` re-anchors it (latest wins, so drift is corrected at once); request latency is not
 * compensated, so the clock runs behind by about half a round trip, which only lengthens a countdown slightly.
 *
 * Until the first response it counts from [serverEpochMillisAtStart] (the stub behaviour of the Detail screen).
 * [timeSource] is injectable for tests.
 */
class MonotonicServerClock(
    serverEpochMillisAtStart: Long = 0L,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : ServerClock,
    ServerTimeSink {
    private class Anchor(
        val serverEpochMillis: Long,
        val mark: TimeMark,
    )

    @Volatile
    private var anchor = Anchor(serverEpochMillisAtStart, timeSource.markNow())

    @Volatile
    var isSynced: Boolean = false
        private set

    override fun nowMillis(): Long {
        val current = anchor
        return current.serverEpochMillis + current.mark.elapsedNow().inWholeMilliseconds
    }

    override fun recordServerNow(serverEpochMillis: Long) {
        anchor = Anchor(serverEpochMillis, timeSource.markNow())
        isSynced = true
    }
}
