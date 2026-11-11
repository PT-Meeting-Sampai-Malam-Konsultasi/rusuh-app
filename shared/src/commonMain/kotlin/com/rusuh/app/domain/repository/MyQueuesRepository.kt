package com.rusuh.app.domain.repository

import com.rusuh.app.domain.model.MyQueue

/**
 * Data seam for the My Queues tab.
 *
 * TODO(FR-APP-01-M4 #50): back with the real my-queues API; map DTOs to [MyQueue]
 * inside the data layer.
 */
interface MyQueuesRepository {
    /** The queues the signed-in user is currently in. Throws on load failure. */
    suspend fun getMyQueues(): List<MyQueue>
}
