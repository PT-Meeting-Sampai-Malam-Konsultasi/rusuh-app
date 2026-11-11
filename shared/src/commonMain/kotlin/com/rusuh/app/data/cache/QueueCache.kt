package com.rusuh.app.data.cache

import com.rusuh.app.data.storage.AppPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The non-secret facts of the last claimed admission, kept for the Detail screen (max tickets and the order window).
 * The admission token itself is a secret and lives in the `TokenStore`. [expiresAtEpochMillis] is on the server
 * timeline. There is one admission at a time, so one entry; it names its event.
 */
@Serializable
data class AdmissionInfo(
    val eventId: String,
    val slotId: String,
    val maxTickets: Int,
    val expiresAtEpochMillis: Long?,
)

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

    suspend fun setAdmission(info: AdmissionInfo) = lock.withLock { write(KEY_ADMISSION, json.encodeToString(info)) }

    @Suppress("SwallowedException")
    suspend fun getAdmission(): AdmissionInfo? =
        lock.withLock {
            read(KEY_ADMISSION)?.let { raw ->
                try {
                    json.decodeFromString<AdmissionInfo>(raw)
                } catch (broken: IllegalArgumentException) {
                    null
                }
            }
        }

    suspend fun clearAdmission() = lock.withLock { delete(KEY_ADMISSION) }

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
        const val KEY_ADMISSION = PREFIX + "admission_json"
        val json = Json
    }
}
