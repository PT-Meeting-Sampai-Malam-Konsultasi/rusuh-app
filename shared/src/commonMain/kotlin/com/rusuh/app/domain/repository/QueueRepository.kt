package com.rusuh.app.domain.repository

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.feature.waitingroom.domain.repository.WaitingRoomRepository

/** Queue actions beyond observing the place ([WaitingRoomRepository.observePlace], which also joins). */
interface QueueRepository : WaitingRoomRepository {
    /**
     * Leaves the queue of [eventId] (`DELETE /queue/{eventId}/me`, idempotent) and forgets the stored place. A
     * device that never joined succeeds without a call.
     */
    suspend fun leave(eventId: String): DomainResult<Unit>
}
