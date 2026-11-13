package com.rusuh.app.domain.usecase

import com.rusuh.app.domain.model.MyQueue
import com.rusuh.app.domain.model.OrderInProgress
import com.rusuh.app.domain.repository.MyQueuesRepository

/** Loads the queues the user is in. Failures propagate to the caller (the ViewModel). */
class GetMyQueuesUseCase(
    private val repository: MyQueuesRepository,
) {
    suspend operator fun invoke(): List<MyQueue> = repository.getMyQueues()

    suspend fun getOrdersInProgress(): List<OrderInProgress> = repository.getOrdersInProgress()
}
