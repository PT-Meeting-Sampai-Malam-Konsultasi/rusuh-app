package com.rusuh.app.data.repository

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.repository.QueueClaimOutcome
import com.rusuh.app.domain.repository.QueueRepository
import com.rusuh.app.feature.waitingroom.domain.repository.WaitingRoomRepository

/** Offline-demo [QueueRepository]: the scripted waiting room, and leaving always succeeds. */
class StubQueueRepository(
    waitingRoom: WaitingRoomRepository,
) : QueueRepository,
    WaitingRoomRepository by waitingRoom {
    override suspend fun claim(
        eventId: String,
        turnExpiresAtEpochMillis: Long,
        serverNowEpochMillis: Long,
    ) = QueueClaimOutcome.Admitted

    override suspend fun resumeAdmission(eventId: String): Boolean = true

    override suspend fun leave(eventId: String): DomainResult<Unit> = DomainResult.Success(Unit)
}
