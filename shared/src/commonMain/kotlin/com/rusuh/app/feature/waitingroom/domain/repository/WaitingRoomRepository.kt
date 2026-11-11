package com.rusuh.app.feature.waitingroom.domain.repository

import com.rusuh.app.feature.waitingroom.domain.model.QueuePlace
import kotlinx.coroutines.flow.Flow

/**
 * Queue-place source for the Waiting Room.
 *
 * The transport behind this seam (polling for everyone, SSE for the last 2,000
 * places, no WebSocket; ADR-0015) is FR-04-M4 (#30) and invisible to callers.
 */
interface WaitingRoomRepository {
    /**
     * Emits the customer's place for [eventId] as it changes. Completes without
     * emitting when the customer has no place; fails the flow on a load error.
     */
    fun observePlace(eventId: String): Flow<QueuePlace>
}
