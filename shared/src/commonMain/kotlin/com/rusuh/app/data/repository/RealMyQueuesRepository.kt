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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * [MyQueuesRepository] over the queues this device joined (the event ids in [QueueCache]): for each one it reads
 * `GET /queue/{eventId}/me` and the frontier, at most [maxParallel] entries at a time, and the event name from one
 * `GET /events` per call (best effort: a failure there only shows the event id as the title).
 *
 * Per entry: a closed queue is dropped and forgotten (token and cached id), and so is a place that ended (410) or
 * whose token the server rejects (401). A 404, a missing frontier or a transient failure only leaves the entry out of
 * this load (it stays stored). The load throws [DomainException] only when EVERY entry failed with an error other than
 * a 404, so one bad entry never blanks the tab.
 */
class RealMyQueuesRepository(
    private val queueApi: QueueApiService,
    private val eventApi: EventApiService,
    private val tokens: TokenStore,
    private val cache: QueueCache,
    private val localeProvider: LocaleProvider,
    private val maxParallel: Int = DEFAULT_PARALLEL,
) : MyQueuesRepository {
    /** What happened to one entry. */
    private sealed interface Entry {
        data class Item(val queue: MyQueue) : Entry

        /** Left out of the list without being an error (closed, ended, 404, no token). */
        data object Skipped : Entry

        data class Failed(val error: DomainException) : Entry
    }

    override suspend fun getMyQueues(): List<MyQueue> {
        val ids = cache.getJoinedEventIds()
        if (ids.isEmpty()) return emptyList()
        val events = loadEvents()
        val permits = Semaphore(maxParallel.coerceAtLeast(1))
        val entries =
            coroutineScope {
                ids.map { id -> async { permits.withPermit { entryOf(id, events[id]) } } }.awaitAll()
            }
        val failures = entries.filterIsInstance<Entry.Failed>()
        if (failures.size == entries.size) throw failures.first().error
        return entries.filterIsInstance<Entry.Item>().map { it.queue }
    }

    private suspend fun loadEvents(): Map<String, EventDto> =
        (eventApi.getEvents() as? ApiResult.Success)?.data?.items.orEmpty().associateBy { it.eventId }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun entryOf(
        eventId: String,
        event: EventDto?,
    ): Entry =
        try {
            queueOf(eventId, event)?.let { Entry.Item(it) } ?: Entry.Skipped
        } catch (failure: DomainException) {
            Entry.Failed(failure)
        }

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

    /** The place, or null to drop the entry (404 keeps it stored, 401 and 410 forget it). */
    private suspend fun readPlace(
        eventId: String,
        token: String,
    ): QueuePlaceDto? =
        when (val result = queueApi.me(eventId, token)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error ->
                when (result.code) {
                    HTTP_NOT_FOUND -> null
                    HTTP_GONE, HTTP_UNAUTHORIZED -> {
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

    companion object {
        /** How many entries are read at the same time. */
        const val DEFAULT_PARALLEL = 4
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_GONE = 410
        private const val HTTP_UNAUTHORIZED = 401
    }
}
