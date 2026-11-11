@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.data.stub

import platform.Foundation.NSRecursiveLock

internal actual class StubLock {
    private val lock = NSRecursiveLock()

    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
