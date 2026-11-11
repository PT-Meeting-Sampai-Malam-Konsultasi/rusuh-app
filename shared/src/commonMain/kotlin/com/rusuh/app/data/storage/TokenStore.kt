package com.rusuh.app.data.storage

/**
 * Session credentials. The access token lives in memory only and is gone after process death;
 * every other token is persisted through [SecureStore].
 */
interface TokenStore {
    var accessToken: String?

    suspend fun getRefreshToken(): String?

    suspend fun setRefreshToken(token: String)

    suspend fun getDeviceToken(): String?

    suspend fun setDeviceToken(token: String)

    suspend fun getQueueToken(eventId: String): String?

    suspend fun setQueueToken(
        eventId: String,
        token: String,
    )

    suspend fun clearQueueToken(eventId: String)

    suspend fun getAdmissionToken(): String?

    suspend fun setAdmissionToken(token: String)

    /** Logout: drops the access, refresh, queue and admission tokens. The device token survives. */
    suspend fun clearSession()
}
