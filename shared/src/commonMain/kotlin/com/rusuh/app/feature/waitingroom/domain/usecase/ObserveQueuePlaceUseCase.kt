package com.rusuh.app.feature.waitingroom.domain.usecase

import com.rusuh.app.feature.waitingroom.domain.model.QueuePlace
import com.rusuh.app.feature.waitingroom.domain.repository.WaitingRoomRepository
import kotlinx.coroutines.flow.Flow

/** Observes the customer's queue place for an event. */
class ObserveQueuePlaceUseCase(
    private val repository: WaitingRoomRepository,
) {
    operator fun invoke(eventId: String): Flow<QueuePlace> = repository.observePlace(eventId)
}
