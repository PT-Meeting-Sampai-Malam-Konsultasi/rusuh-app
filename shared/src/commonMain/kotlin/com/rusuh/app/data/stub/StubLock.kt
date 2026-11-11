package com.rusuh.app.data.stub

/**
 * Re-entrant mutual exclusion for the stub backend's shared state (script indexes, request log, clock), which
 * parallel requests touch. Kotlin has no common `synchronized`, so each platform supplies one.
 */
internal expect class StubLock() {
    fun <T> withLock(block: () -> T): T
}
