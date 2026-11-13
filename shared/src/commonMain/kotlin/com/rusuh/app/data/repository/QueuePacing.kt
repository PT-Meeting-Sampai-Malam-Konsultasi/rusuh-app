package com.rusuh.app.data.repository

import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * Timing of the queue calls (ADR-0003). Injectable so tests run in virtual time with a seeded [random]:
 * [delay] waits (the default is the coroutine `delay`, which virtual time controls), [random] is the jitter source and
 * [nowMillis] a monotonic clock for budgets such as the 60 s turn window (it must advance with [delay] in tests).
 */
class QueuePacing(
    val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    val random: Random = Random.Default,
    val nowMillis: () -> Long = monotonicMillis(),
) {
    /**
     * Wait after the [failures]th consecutive transient failure: 2, 4, 8, 16, then 30 s (capped), but at least the
     * server's `Retry-After` (itself capped at [MAX_RETRY_AFTER_MILLIS]).
     */
    fun backoffMillis(
        failures: Int,
        retryAfterSeconds: Int?,
    ): Long {
        val exponential =
            (BACKOFF_START_MILLIS shl (failures - 1).coerceIn(0, MAX_BACKOFF_SHIFT)).coerceAtMost(
                BACKOFF_CAP_MILLIS,
            )
        val hint = (retryAfterSeconds ?: 0) * MILLIS_PER_SECOND
        return maxOf(exponential, hint.coerceAtMost(MAX_RETRY_AFTER_MILLIS))
    }

    /**
     * Wait before the next frontier poll for a place [position] spots from the front: 10 s when more than 10,000
     * places back, 5 s within 10,000, 2 s within 1,000, each with +-20% jitter. A null [position] (the draw has not
     * given a number yet) polls at the middle cadence.
     */
    fun pollDelayMillis(position: Int?): Long = jittered(baseIntervalMillis(position))

    /** [baseMillis] with +-20% jitter. */
    fun jittered(baseMillis: Long): Long {
        val unit = random.nextDouble() * 2 - 1 // -1..1
        return (baseMillis * (1.0 + JITTER_FRACTION * unit)).roundToLong()
    }

    /**
     * Wait before the next claim attempt: `NOT_YET` about 1 s, `FULL` ([full]) 1-2 s, both with jitter; a
     * `Retry-After` hint ([retryAfterSeconds]) replaces the 1 s.
     */
    fun claimRetryMillis(
        full: Boolean,
        retryAfterSeconds: Int?,
    ): Long {
        val base = (retryAfterSeconds?.takeIf { it > 0 } ?: 1) * CLAIM_RETRY_MILLIS
        return if (full) base + randomUpTo(CLAIM_RETRY_MILLIS) else jittered(base)
    }

    /**
     * Waits before the next claim attempt, never past [deadline] (on [nowMillis]). Returns true when the wait was cut
     * to the deadline, i.e. the next attempt is the last one.
     */
    suspend fun waitForClaimRetry(
        full: Boolean,
        retryAfterSeconds: Int?,
        deadline: Long,
    ): Boolean {
        val wait = claimRetryMillis(full, retryAfterSeconds)
        val remaining = (deadline - nowMillis()).coerceAtLeast(0L)
        delay(minOf(wait, remaining))
        return wait >= remaining
    }

    /** A random wait in `0..maxMillis`, for the one-off `GET /me` after the draw. */
    fun randomUpTo(maxMillis: Long): Long = (random.nextDouble() * maxMillis).roundToLong()

    companion object {
        const val FAR_INTERVAL_MILLIS = 10_000L
        const val NEAR_INTERVAL_MILLIS = 5_000L
        const val CLOSE_INTERVAL_MILLIS = 2_000L
        const val FAR_POSITION = 10_000
        const val NEAR_POSITION = 1_000
        const val JITTER_FRACTION = 0.2
        const val CLAIM_RETRY_MILLIS = 1_000L
        const val BACKOFF_START_MILLIS = 2_000L
        const val BACKOFF_CAP_MILLIS = 30_000L
        const val MAX_RETRY_AFTER_MILLIS = 120_000L
        const val MAX_CONSECUTIVE_FAILURES = 5
        private const val MAX_BACKOFF_SHIFT = 6
        private const val MILLIS_PER_SECOND = 1_000L

        // SSE stream timings (ADR-0015)
        const val SSE_FIRST_EVENT_TIMEOUT_MILLIS = 5_000L
        const val SSE_SILENCE_TIMEOUT_MILLIS = 30_000L
        const val SSE_RETRY_DELAY_MILLIS = 60_000L
        const val SSE_DEFAULT_RETRY_MILLIS = 3_000L
        const val SSE_MAX_CONNECT_FAILURES = 2

        private fun monotonicMillis(): () -> Long {
            val origin = TimeSource.Monotonic.markNow()
            return { origin.elapsedNow().inWholeMilliseconds }
        }

        /** The cadence of ADR-0003 without jitter. */
        fun baseIntervalMillis(position: Int?): Long =
            when {
                position == null -> NEAR_INTERVAL_MILLIS
                position > FAR_POSITION -> FAR_INTERVAL_MILLIS
                position > NEAR_POSITION -> NEAR_INTERVAL_MILLIS
                else -> CLOSE_INTERVAL_MILLIS
            }
    }
}
