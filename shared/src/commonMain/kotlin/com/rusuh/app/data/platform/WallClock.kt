package com.rusuh.app.data.platform

/**
 * The device wall clock in epoch millis. NOT a source of server time: it is only the conservative fallback for
 * judging a cached device-token expiry before the first server response has synced the `ServerClock`.
 */
expect fun wallClockMillis(): Long
