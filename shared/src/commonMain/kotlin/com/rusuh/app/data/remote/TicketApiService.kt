package com.rusuh.app.data.remote

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.remote.dto.EventDto
import com.rusuh.app.data.remote.dto.TicketDto
import com.rusuh.app.data.remote.dto.TicketsResponseDto

/**
 * Tickets endpoints (`GET /core/tickets`, `GET /core/tickets/{ticketId}`). Both require Bearer auth;
 * unauthenticated calls return 401.
 */
class TicketApiService(
    private val api: ApiClient,
) {
    /** All issued tickets of the signed-in user. */
    suspend fun getTickets(): ApiResult<TicketsResponseDto> = api.get(EP_TICKETS)

    /** One ticket by its id. */
    suspend fun getTicket(ticketId: String): ApiResult<TicketDto> = api.get("$EP_TICKETS/$ticketId")

    /** Public event details for enriching ticket display. */
    suspend fun getEvent(eventId: String): ApiResult<EventDto> = api.get("$EP_EVENTS/$eventId")

    private companion object {
        const val EP_TICKETS = "/core/tickets"
        const val EP_EVENTS = "/core/events"
    }
}
