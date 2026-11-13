package com.rusuh.app.data.remote

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.remote.dto.EventDto
import com.rusuh.app.data.remote.dto.EventsResponseDto

/** `GET /core/events` and `GET /core/events/{eventId}` (core service). */
class EventApiService(
    private val api: ApiClient,
) {
    suspend fun getEvents(): ApiResult<EventsResponseDto> = api.get(EP_EVENTS)

    suspend fun getEvent(eventId: String): ApiResult<EventDto> = api.get("$EP_EVENTS/${pathSegment(eventId)}")

    private companion object {
        const val EP_EVENTS = "/core/events"
    }
}
