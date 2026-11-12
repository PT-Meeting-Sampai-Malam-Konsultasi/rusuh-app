package com.rusuh.app.data.storage

/**
 * [TokenStore] that lives in memory only: nothing touches the Keystore / Keychain. Used by the Stub demo mode, so
 * its placeholder tokens never reach the real [SecureStore], and a stub run always starts signed out.
 */
@Suppress("TooManyFunctions") // The TokenStore interface defines the surface.
class InMemoryTokenStore : TokenStore {
    private var refresh: String? = null
    private var device: String? = null
    private var admission: String? = null
    private val queue = mutableMapOf<String, String>()

    override var accessToken: String? = null

    override suspend fun getRefreshToken(): String? = refresh

    override suspend fun setRefreshToken(token: String) {
        refresh = token
    }

    override suspend fun clearRefreshToken() {
        refresh = null
    }

    override suspend fun getDeviceToken(): String? = device

    override suspend fun setDeviceToken(token: String) {
        device = token
    }

    override suspend fun getQueueToken(eventId: String): String? = queue[eventId]

    override suspend fun setQueueToken(
        eventId: String,
        token: String,
    ) {
        queue[eventId] = token
    }

    override suspend fun clearQueueToken(eventId: String) {
        queue.remove(eventId)
    }

    override suspend fun getAdmissionToken(): String? = admission

    override suspend fun setAdmissionToken(token: String) {
        admission = token
    }

    override suspend fun clearAdmissionToken() {
        admission = null
    }

    override suspend fun clearSession() {
        accessToken = null
        refresh = null
        admission = null
        queue.clear()
    }
}
