package com.rusuh.app.data.remote.mapper

/** Wire values of `QueueState` and of the `reason` member of the frontier. */
internal object QueueWire {
    const val PREOPEN = "preopen"
    const val OPEN = "open"
    const val PAUSED = "paused"
    const val CLOSED = "closed"
    const val REASON_STOCK = "stock"
}
