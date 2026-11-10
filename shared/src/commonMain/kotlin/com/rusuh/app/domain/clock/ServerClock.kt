package com.rusuh.app.domain.clock

/**
 * Source of "server now" in epoch milliseconds. Order-window logic (remaining = expiresAt -
 * serverNow) must read time ONLY through this seam, never from the device clock, so it can be
 * faked in tests and later synced with the server (FR-17, TODO(FR-17-M1 #82)).
 */
fun interface ServerClock {
    fun nowMillis(): Long
}
