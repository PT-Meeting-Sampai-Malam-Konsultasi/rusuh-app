package com.rusuh.app.data.cache

import com.rusuh.app.data.storage.AppPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Queue module cache: the ids of the events this device joined a queue for (a JSON list), so My Queues knows which
 * places to read. The queue tokens themselves live in the `TokenStore`, never here. Changes are serialised, so two
 * joins at once never lose an id; an unreadable stored value counts as an empty list.
 */
class QueueCache(
    prefs: AppPreferences,
) : PrefsCache(prefs, PREFIX) {
    private val lock = Mutex()

    suspend fun getJoinedEventIds(): List<String> = lock.withLock { readIds() }

    suspend fun addJoinedEvent(eventId: String) =
        lock.withLock {
            val ids = readIds()
            if (eventId !in ids) write(KEY_JOINED, encode(ids + eventId))
        }

    suspend fun removeJoinedEvent(eventId: String) =
        lock.withLock {
            val ids = readIds()
            if (eventId in ids) write(KEY_JOINED, encode(ids - eventId))
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun readIds(): List<String> =
        read(KEY_JOINED)?.let { raw ->
            try {
                json.decodeFromString(ListSerializer(String.serializer()), raw)
            } catch (broken: IllegalArgumentException) {
                emptyList()
            }
        } ?: emptyList()

    private fun encode(ids: List<String>): String = json.encodeToString(ListSerializer(String.serializer()), ids)

    private companion object {
        const val PREFIX = "queue_"
        const val KEY_JOINED = PREFIX + "joined_events_json"
        val json = Json
    }
}
