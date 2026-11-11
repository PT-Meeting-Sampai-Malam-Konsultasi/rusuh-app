package com.rusuh.app.domain.clock

/**
 * Source of "server now" in epoch milliseconds. Order-window logic (remaining = expiresAt -
 * serverNow) must read time ONLY through this seam, never from the device clock, so it can be
 * faked in tests and stays synced with the server (FR-17, FR-19, FR-21).
 */
fun interface ServerClock {
    fun nowMillis(): Long

    /**
     * False until a server time has been seen (the clock then counts from an arbitrary start, so [nowMillis] must not
     * be compared with server timestamps). Fixed clocks such as the stub demo clock are always synced.
     */
    val isSynced: Boolean get() = true
}

/** Milliseconds left until [expiresAtMillis] on the server timeline; never negative. */
fun ServerClock.remainingMillis(expiresAtMillis: Long): Long = (expiresAtMillis - nowMillis()).coerceAtLeast(0L)
