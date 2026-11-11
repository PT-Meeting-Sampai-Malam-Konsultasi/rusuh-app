@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.data.stub

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal actual class StubLock {
    private val lock = ReentrantLock()

    actual fun <T> withLock(block: () -> T): T = lock.withLock(block)
}
