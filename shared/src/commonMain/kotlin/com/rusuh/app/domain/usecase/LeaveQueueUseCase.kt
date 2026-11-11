package com.rusuh.app.domain.usecase

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.repository.QueueRepository

/** Leaves the queue of an event ("Keluar dari antrian"). */
class LeaveQueueUseCase(
    private val repository: QueueRepository,
) {
    suspend operator fun invoke(eventId: String): DomainResult<Unit> = repository.leave(eventId)
}
