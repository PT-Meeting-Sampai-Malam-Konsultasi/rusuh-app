package com.rusuh.app.data.repository

import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Timing of the queue calls (ADR-0003). Injectable so tests run in virtual time with a seeded [random]:
 * [delay] waits (the default is the coroutine `delay`, which virtual time controls) and [random] is the jitter source.
 */
class QueuePacing(
    val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    val random: Random = Random.Default,
) {
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

    /** A random wait in `0..maxMillis`, for the one-off `GET /me` after the draw. */
    fun randomUpTo(maxMillis: Long): Long = (random.nextDouble() * maxMillis).roundToLong()

    companion object {
        const val FAR_INTERVAL_MILLIS = 10_000L
        const val NEAR_INTERVAL_MILLIS = 5_000L
        const val CLOSE_INTERVAL_MILLIS = 2_000L
        const val FAR_POSITION = 10_000
        const val NEAR_POSITION = 1_000
        const val JITTER_FRACTION = 0.2

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
