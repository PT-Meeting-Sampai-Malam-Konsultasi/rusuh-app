package com.rusuh.app.data.stub

import com.rusuh.app.domain.clock.ServerClock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Injectable "server now" for the stub backend. Stub payloads carry time placeholders (`@{now}`, `@{now+300}`,
 * `@{epoch}`) that [render] resolves against this clock, so a countdown test advances the clock instead of
 * sleeping. Also a [ServerClock], so the same instance can drive the domain code under test.
 *
 * Frozen by default (tests: time moves only through [advanceBy] / [setTo]). The demo uses [ticking], which also
 * advances with a monotonic source, so countdowns run. Safe to use from several threads.
 */
class StubClock(
    startMillis: Long = DEFAULT_START_MILLIS,
    private val ticking: Boolean = false,
) : ServerClock {
    private val lock = StubLock()
    private val mark = TimeSource.Monotonic.markNow()
    private var baseMillis = startMillis

    override fun nowMillis(): Long = lock.withLock { current() }

    fun advanceBy(duration: Duration) {
        lock.withLock { baseMillis += duration.inWholeMilliseconds }
    }

    fun setTo(millis: Long) {
        lock.withLock { baseMillis += millis - current() }
    }

    /** ISO-8601 UTC instant (`2026-11-14T10:40:00Z`) at now plus [offsetSeconds]. */
    fun isoAt(offsetSeconds: Long = 0): String = formatIso(nowMillis() + offsetSeconds * MILLIS_PER_SECOND)

    /**
     * Replaces every placeholder of [template]: `@{now}` / `@{now+N}` / `@{now-N}` become ISO instants and
     * `@{epoch}` / `@{epoch+N}` epoch seconds, with N in seconds. All placeholders see the same instant.
     */
    fun render(template: String): String {
        val now = nowMillis()
        return PLACEHOLDER.replace(template) { match ->
            val offset = match.groupValues[2].toLongOrNull() ?: 0L
            when (match.groupValues[1]) {
                KIND_EPOCH -> ((now / MILLIS_PER_SECOND) + offset).toString()
                else -> formatIso(now + offset * MILLIS_PER_SECOND)
            }
        }
    }

    private fun current(): Long = baseMillis + if (ticking) mark.elapsedNow().inWholeMilliseconds else 0L

    companion object {
        /** 2026-11-14T10:40:00Z, the `serverNow` of the spec examples. */
        const val DEFAULT_START_MILLIS = 1_794_652_800_000L

        /** A clock that starts at [startMillis] and keeps running, for the demo mode. */
        fun ticking(startMillis: Long = DEFAULT_START_MILLIS): StubClock = StubClock(startMillis, ticking = true)

        private const val MILLIS_PER_SECOND = 1000L
        private const val KIND_EPOCH = "epoch"
        private val PLACEHOLDER = Regex("""@\{(now|epoch)([+-]\d+)?}""")

        private fun formatIso(epochMillis: Long): String =
            Instant.fromEpochSeconds(epochMillis.floorDiv(MILLIS_PER_SECOND)).toString()
    }
}
