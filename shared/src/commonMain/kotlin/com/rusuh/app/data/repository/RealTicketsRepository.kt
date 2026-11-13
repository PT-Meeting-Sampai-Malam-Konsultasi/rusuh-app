package com.rusuh.app.data.repository

import com.rusuh.app.data.cache.CachedEventFacts
import com.rusuh.app.data.cache.TicketsCache
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.LocaleProvider
import com.rusuh.app.data.remote.TicketApiService
import com.rusuh.app.data.remote.dto.TicketDto
import com.rusuh.app.data.remote.mapper.groupTicketsIntoOrders
import com.rusuh.app.data.remote.mapper.ticketGroupToETicketOrder
import com.rusuh.app.data.remote.mapper.toCachedEventFacts
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.DomainException
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.feature.eticket.ETicketOrder
import com.rusuh.app.feature.tickets.TicketOrder
import com.rusuh.app.feature.tickets.TicketsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [TicketsRepository] over `GET /core/tickets` and `GET /core/events/{eventId}`.
 *
 * Event facts (title, venue, doors-open time, poster tone) are fetched per unique eventId and cached in
 * [TicketsCache] so the e-ticket page does not refetch for every ticket of the same event.
 *
 * shortcut: The `GET /core/tickets` endpoint returns flat tickets without order-level fields (order number,
 * total, payment method, paid-at time, contact email). These fields are left blank; the UI handles missing
 * data gracefully. Document this gap in docs/data-layer.md.
 *
 * A failed call throws [DomainException], which the use case catches and presents as an error state with retry.
 * A 401 is handled by the session layer (refresh or session expired).
 */
class RealTicketsRepository(
    private val api: TicketApiService,
    private val cache: TicketsCache,
    private val localeProvider: LocaleProvider,
    private val serverClock: ServerClock,
) : TicketsRepository {
    /** In-memory cache for event facts during a session, keyed by eventId. */
    private val eventFactsMemory = mutableMapOf<String, CachedEventFacts>()
    private val mutex = Mutex()

    override suspend fun getMyOrders(): List<TicketOrder> {
        val tickets = fetchTickets()
        val eventIds = tickets.mapNotNull { it.eventId.takeIf { id -> id.isNotBlank() } }.toSet()
        ensureEventFacts(eventIds)
        // Build a snapshot of event facts for the mapper (non-suspend lambda)
        val eventFactsSnapshot = eventIds.associateWith { getEventFactsSync(it) }
        val nowMillis = serverClock.takeIf { it.isSynced }?.nowMillis()
        return groupTicketsIntoOrders(
            tickets = tickets,
            eventFactsProvider = { eventId -> eventFactsSnapshot[eventId] },
            language = localeProvider.language(),
            nowMillis = nowMillis,
        )
    }

    override suspend fun getOrderTickets(orderId: String): ETicketOrder? =
        orderId.takeIf { it.isNotBlank() }?.let { buildETicketOrder(it) }

    private suspend fun buildETicketOrder(orderId: String): ETicketOrder? {
        val tickets = fetchTickets()
        val orderTickets = tickets.filter { it.bookingId == orderId }
        if (orderTickets.isEmpty()) return null

        val eventId = orderTickets.first().eventId
        if (eventId.isNotBlank()) {
            ensureEventFacts(setOf(eventId))
        }
        return ticketGroupToETicketOrder(
            bookingId = orderId,
            tickets = orderTickets,
            eventFacts = getEventFacts(eventId),
        )
    }

    private suspend fun fetchTickets(): List<TicketDto> {
        val result = api.getTickets().toDomainResult()
        if (result !is DomainResult.Success) throw DomainException(result)
        return result.data.items
    }

    /**
     * Ensures event facts are cached for all given event IDs. Fetches from API if not in memory/disk cache.
     * Failures are ignored (the ticket display degrades gracefully with missing event facts).
     */
    private suspend fun ensureEventFacts(eventIds: Set<String>) {
        eventIds
            .filter { it.isNotBlank() && getEventFacts(it) == null }
            .forEach { fetchAndCacheEventFacts(it) }
    }

    private suspend fun fetchAndCacheEventFacts(eventId: String) {
        try {
            when (val result = api.getEvent(eventId)) {
                is ApiResult.Success -> {
                    val facts = result.data.toCachedEventFacts()
                    mutex.withLock { eventFactsMemory[eventId] = facts }
                    runCatching { cache.setEventFacts(eventId, facts) }
                }
                else -> {
                    // Event fetch failed; degrade gracefully (ticket shows without event facts)
                }
            }
        } catch (_: Exception) {
            // Silently fail; the ticket display degrades gracefully
        }
    }

    private suspend fun getEventFacts(eventId: String): CachedEventFacts? {
        if (eventId.isBlank()) return null
        val memoryFacts = mutex.withLock { eventFactsMemory[eventId] }
        val result = memoryFacts ?: runCatching { cache.getEventFacts(eventId) }.getOrNull()
        result?.let { mutex.withLock { eventFactsMemory[eventId] = it } }
        return result
    }

    /** Synchronous read from the in-memory cache only (for the mapper lambda after ensureEventFacts). */
    private fun getEventFactsSync(eventId: String): CachedEventFacts? =
        if (eventId.isBlank()) null else eventFactsMemory[eventId]
}
