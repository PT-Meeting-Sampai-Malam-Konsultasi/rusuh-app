package com.rusuh.app.domain.usecase

import com.rusuh.app.domain.repository.QueueClaimOutcome
import com.rusuh.app.domain.repository.QueueRepository

class ClaimQueueUseCase(private val repository: QueueRepository) {
    suspend operator fun invoke(
        eventId: String,
        expiresAt: Long,
        serverNow: Long,
    ): QueueClaimOutcome = repository.claim(eventId, expiresAt, serverNow)
}
