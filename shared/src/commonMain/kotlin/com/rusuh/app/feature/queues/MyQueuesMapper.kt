package com.rusuh.app.feature.queues

import com.rusuh.app.domain.model.MyQueue
import com.rusuh.app.domain.model.MyQueuePhase
import com.rusuh.app.ui.components.QueueListItemUi
import com.rusuh.app.ui.components.QueueListPhase

/**
 * Maps domain queues to row models. [QueueListItemUi] `require`s its invariants, so
 * a bad row is dropped here instead of crashing the screen; rows repeating an
 * already-seen event id are dropped too (the list uses the id as its key).
 */
internal fun List<MyQueue>.toUiItems(): List<QueueListItemUi> {
    val seen = mutableSetOf<String>()
    return mapNotNull { queue -> queue.toUiOrNull()?.takeIf { seen.add(it.eventId) } }
}

internal fun MyQueue.toUiOrNull(): QueueListItemUi? =
    try {
        QueueListItemUi(
            eventId = eventId,
            title = title,
            dateVenue = dateVenue,
            position = position,
            nowServing = nowServing,
            phase = phase.toUi(),
            updatedAgoSeconds = updatedAgoSeconds,
            etaMinutes = etaMinutes,
        )
    } catch (
        @Suppress("SwallowedException") invalid: IllegalArgumentException,
    ) {
        null
    }

private fun MyQueuePhase.toUi(): QueueListPhase =
    when (this) {
        MyQueuePhase.QueueOpen -> QueueListPhase.QueueOpen
        MyQueuePhase.Live -> QueueListPhase.Live
        MyQueuePhase.Paused -> QueueListPhase.Paused
        MyQueuePhase.YourTurn -> QueueListPhase.YourTurn
        MyQueuePhase.Closed -> QueueListPhase.Closed
    }
