package com.rusuh.app.data.cache

import com.rusuh.app.data.storage.AppPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Tickets module cache: event facts (title, venue, doors-open time, poster tone) per eventId, so the
 * e-ticket page does not refetch for every ticket of the same event.
 */
class TicketsCache(
    prefs: AppPreferences,
) : PrefsCache(prefs, PREFIX) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getEventFacts(eventId: String): CachedEventFacts? =
        read(keyFor(eventId))?.let { runCatching { json.decodeFromString<CachedEventFacts>(it) }.getOrNull() }

    suspend fun setEventFacts(
        eventId: String,
        facts: CachedEventFacts,
    ) = write(keyFor(eventId), json.encodeToString(facts))

    suspend fun clearEvent(eventId: String) = delete(keyFor(eventId))

    private fun keyFor(eventId: String): String = "${PREFIX}event_$eventId"

    private companion object {
        const val PREFIX = "tix_"
    }
}

/**
 * Event facts needed by the e-ticket display, cached per event. Times are epoch millis; nullable when
 * not available from the event endpoint.
 */
@Serializable
data class CachedEventFacts(
    val eventName: String,
    val venueShort: String,
    val timezone: String,
    val doorsOpenAtMillis: Long?,
    val startsAtMillis: Long?,
)
