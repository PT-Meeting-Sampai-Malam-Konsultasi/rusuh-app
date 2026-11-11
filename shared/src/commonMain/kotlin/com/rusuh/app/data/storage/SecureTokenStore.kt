package com.rusuh.app.data.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** [TokenStore] backed by a [SecureStore]. Owns every secure key string. */
class SecureTokenStore(
    private val store: SecureStore,
) : TokenStore {
    private val sessionLock = Mutex()

    override var accessToken: String? = null

    override suspend fun getRefreshToken(): String? = store.getString(KEY_REFRESH)

    override suspend fun setRefreshToken(token: String) = store.putString(KEY_REFRESH, token)

    override suspend fun getDeviceToken(): String? = store.getString(KEY_DEVICE)

    override suspend fun setDeviceToken(token: String) = store.putString(KEY_DEVICE, token)

    override suspend fun getQueueToken(eventId: String): String? = store.getString(queueKey(eventId))

    override suspend fun setQueueToken(
        eventId: String,
        token: String,
    ) = store.putString(queueKey(eventId), token)

    override suspend fun clearQueueToken(eventId: String) = store.remove(queueKey(eventId))

    override suspend fun getAdmissionToken(): String? = store.getString(KEY_ADMISSION)

    override suspend fun setAdmissionToken(token: String) = store.putString(KEY_ADMISSION, token)

    override suspend fun clearSession() {
        accessToken = null
        sessionLock.withLock {
            val device = store.getString(KEY_DEVICE)
            store.clear()
            if (device != null) store.putString(KEY_DEVICE, device)
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
