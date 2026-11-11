package com.rusuh.app.data.repository

import com.rusuh.app.data.cache.QueueCache
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.LocaleProvider
import com.rusuh.app.data.remote.EventApiService
import com.rusuh.app.data.remote.QueueApiService
import com.rusuh.app.data.remote.dto.EventDto
import com.rusuh.app.data.remote.dto.FrontierDto
import com.rusuh.app.data.remote.dto.QueuePlaceDto
import com.rusuh.app.data.remote.mapper.QueueWire
import com.rusuh.app.data.remote.mapper.toDateVenueLine
import com.rusuh.app.data.storage.TokenStore
import com.rusuh.app.domain.model.DomainException
import com.rusuh.app.domain.model.MyQueue
import com.rusuh.app.domain.model.MyQueuePhase
import com.rusuh.app.domain.repository.MyQueuesRepository

/**
 * [MyQueuesRepository] over the queues this device joined (the event ids in [QueueCache]): for each one it reads
 * `GET /queue/{eventId}/me` and the frontier, and the event name from one `GET /events` per call (best effort: a
 * failure there only shows the event id as the title).
 *
 * An entry is dropped from the list when its queue is closed (also forgotten: token and cached id), its place ended
 * (410, forgotten), or it answers 404 or has no stored token. A 404 is not forgotten, since the backend may only be
 * missing the route; every other failure throws [DomainException] so the screen shows its error state.
 */
class RealMyQueuesRepository(
    private val queueApi: QueueApiService,
    private val eventApi: EventApiService,
    private val tokens: TokenStore,
    private val cache: QueueCache,
    private val localeProvider: LocaleProvider,
) : MyQueuesRepository {
    override suspend fun getMyQueues(): List<MyQueue> {
        val ids = cache.getJoinedEventIds()
        if (ids.isEmpty()) return emptyList()
        val events = loadEvents()
        return ids.mapNotNull { id -> queueOf(id, events[id]) }
    }

    private suspend fun loadEvents(): Map<String, EventDto> =
        (eventApi.getEvents() as? ApiResult.Success)?.data?.items.orEmpty().associateBy { it.eventId }

    private suspend fun queueOf(
        eventId: String,
        event: EventDto?,
    ): MyQueue? {
        val token = tokens.getQueueToken(eventId)
        val place = if (token == null) null else readPlace(eventId, token)
        val frontier = place?.let { readFrontier(eventId) }
        return when {
            token == null -> {
                cache.removeJoinedEvent(eventId)
                null
            }
            place == null || frontier == null -> null
            place.state == QueueWire.CLOSED || frontier.state == QueueWire.CLOSED -> {
                forget(eventId)
                null
            }
            else -> toMyQueue(eventId, event, place, frontier)
        }
    }

    /** The place, or null to drop the entry (404 keeps it stored, 410 forgets it). */
    private suspend fun readPlace(
        eventId: String,
        token: String,
    ): QueuePlaceDto? =
        when (val result = queueApi.me(eventId, token)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error ->
                when (result.code) {
                    HTTP_NOT_FOUND -> null
                    HTTP_GONE -> {
                        forget(eventId)
                        null
                    }
                    else -> throw result.toException()
                }
            is ApiResult.Failure -> throw result.toException()
        }

    private suspend fun readFrontier(eventId: String): FrontierDto? =
        when (val result = queueApi.frontier(eventId)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> if (result.code == HTTP_NOT_FOUND) null else throw result.toException()
            is ApiResult.Failure -> throw result.toException()
        }

    private suspend fun forget(eventId: String) {
        tokens.clearQueueToken(eventId)
        cache.removeJoinedEvent(eventId)
    }

    private fun toMyQueue(
        eventId: String,
        event: EventDto?,
        place: QueuePlaceDto,
        frontier: FrontierDto,
    ): MyQueue {
        val seq = place.seq
        val preOpen = frontier.state == QueueWire.PREOPEN
        val isTurn = !preOpen && frontier.state == QueueWire.OPEN && seq != null && frontier.served >= seq
        val position = ((seq ?: 0) - frontier.served).coerceAtLeast(1)
        val phase =
            when {
                preOpen -> MyQueuePhase.QueueOpen
                frontier.state == QueueWire.PAUSED -> MyQueuePhase.Paused
                isTurn -> MyQueuePhase.YourTurn
                else -> MyQueuePhase.Live
            }
        return MyQueue(
            eventId = eventId,
            title = event?.name?.ifBlank { null } ?: eventId,
            dateVenue = event?.toDateVenueLine(localeProvider.language()).orEmpty(),
            position = if (isTurn) 0 else position,
            nowServing = if (preOpen) null else frontier.served,
            phase = phase,
            updatedAgoSeconds = 0,
            etaMinutes = if (phase == MyQueuePhase.Live) etaMinutes(position, frontier.ratePerMin) else null,
        )
    }

    /** Wait in whole minutes, rounded up (`position / ratePerMin`); null when the rate is unknown or zero. */
    private fun etaMinutes(
        position: Int,
        ratePerMin: Int?,
    ): Int? = ratePerMin?.takeIf { it > 0 }?.let { (position + it - 1) / it }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val HTTP_GONE = 410
    }
}
