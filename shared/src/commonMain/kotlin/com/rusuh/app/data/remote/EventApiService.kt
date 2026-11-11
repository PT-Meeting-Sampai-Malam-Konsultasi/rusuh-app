package com.rusuh.app.data.remote

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.remote.dto.EventsResponseDto

/** `GET /events` (core service). */
class EventApiService(
    private val api: ApiClient,
) {
    suspend fun getEvents(): ApiResult<EventsResponseDto> = api.get(EP_EVENTS)

    private companion object {
        const val EP_EVENTS = "/events"
    }
}
