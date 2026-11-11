package com.rusuh.app.data.clock

import com.rusuh.app.domain.clock.ServerClock
import kotlin.concurrent.Volatile
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Receives the server time seen in a response, with the monotonic mark of when its request was sent. */
interface ServerTimeSink {
    /** A mark on the sink's own monotonic time source, taken when a request is sent. */
    fun markNow(): TimeMark

    fun recordServerNow(
        serverEpochMillis: Long,
        sentAt: TimeMark,
    )
}

/**
 * The app's [ServerClock]: server time = the last recorded `serverNow` plus the monotonic time elapsed since it was
 * recorded, i.e. a stored `serverNow - monotonicNow` offset. It never jumps with device clock changes.
 *
 * Every response carrying `serverNow` re-anchors it, with two corrections:
 * - Latency: `serverNow` was produced somewhere between send and receive, so the anchor is
 *   `serverNow + (receivedAt - sentAt) / 2` (the midpoint estimate; the error is at most half the round trip).
 * - Order: a response whose request was sent BEFORE the request of the current anchor is ignored, so a slow, old
 *   response arriving late never moves the clock backwards.
 *
 * Until the first response it counts from [serverEpochMillisAtStart] (the stub behaviour of the Detail screen) and
 * [isSynced] is false: callers where a wrong time matters (a payment countdown) gate on [isSynced].
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
        val sentAt: TimeMark?,
    )

    @Volatile
    private var anchor = Anchor(serverEpochMillisAtStart, timeSource.markNow(), sentAt = null)

    @Volatile
    override var isSynced: Boolean = false
        private set

    override fun nowMillis(): Long {
        val current = anchor
        return current.serverEpochMillis + current.mark.elapsedNow().inWholeMilliseconds
    }

    override fun markNow(): TimeMark = timeSource.markNow()

    override fun recordServerNow(
        serverEpochMillis: Long,
        sentAt: TimeMark,
    ) {
        val current = anchor
        // Older send time = larger elapsed time. Equal marks are accepted.
        val isStale = current.sentAt?.let { sentAt.elapsedNow() > it.elapsedNow() } == true
        if (!isStale) {
            val halfTrip = sentAt.elapsedNow().inWholeMilliseconds / 2
            anchor = Anchor(serverEpochMillis + halfTrip, timeSource.markNow(), sentAt)
            isSynced = true
        }
    }

    /** Records [serverEpochMillis] as seen right now, with no latency correction. */
    fun recordServerNow(serverEpochMillis: Long) = recordServerNow(serverEpochMillis, timeSource.markNow())
}
