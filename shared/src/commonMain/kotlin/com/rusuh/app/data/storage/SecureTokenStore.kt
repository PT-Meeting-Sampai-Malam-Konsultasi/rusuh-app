package com.rusuh.app.data.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * [TokenStore] backed by a [SecureStore]. Owns every secure key string. Writes and logout share one lock, so a
 * token set during logout is either fully kept or fully removed, and the device token is never touched by logout.
 */
@Suppress("TooManyFunctions") // The TokenStore interface defines the surface.
class SecureTokenStore(
    private val store: SecureStore,
) : TokenStore {
    private val sessionLock = Mutex()

    @Volatile
    override var accessToken: String? = null

    override suspend fun getRefreshToken(): String? = store.getString(KEY_REFRESH)

    override suspend fun setRefreshToken(token: String) = sessionLock.withLock { store.putString(KEY_REFRESH, token) }

    override suspend fun getDeviceToken(): String? = store.getString(KEY_DEVICE)

    override suspend fun setDeviceToken(token: String) = sessionLock.withLock { store.putString(KEY_DEVICE, token) }

    override suspend fun getQueueToken(eventId: String): String? = store.getString(queueKey(eventId))

    override suspend fun setQueueToken(
        eventId: String,
        token: String,
    ) = sessionLock.withLock { store.putString(queueKey(eventId), token) }

    override suspend fun clearQueueToken(eventId: String) = sessionLock.withLock { store.remove(queueKey(eventId)) }

    override suspend fun getAdmissionToken(): String? = store.getString(KEY_ADMISSION)

    override suspend fun setAdmissionToken(token: String) =
        sessionLock.withLock {
            store.putString(
                KEY_ADMISSION,
                token,
            )
        }

    override suspend fun clearSession() {
        accessToken = null
        sessionLock.withLock {
            var failure: Throwable? = null
            val removals: List<suspend () -> Unit> =
                listOf(
                    { store.remove(KEY_REFRESH) },
                    { store.remove(KEY_ADMISSION) },
                    { store.removeWhere(QUEUE_PREFIX) },
                )
            for (removal in removals) {
                try {
                    removal()
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Throwable,
                ) {
                    // Keep going so one failing removal never leaves the other tokens behind.
                    failure = failure ?: e
                }
            }
            failure?.let { throw it }
        }
    }

    private companion object {
        const val KEY_REFRESH = "auth_refresh_token"
        const val KEY_DEVICE = "auth_device_token"
        const val KEY_ADMISSION = "book_admission_token"
    }
}

private const val QUEUE_PREFIX = "queue_token_"

private fun queueKey(eventId: String): String {
    require(eventId.isNotBlank()) { "eventId must not be blank" }
    return QUEUE_PREFIX + eventId
}
