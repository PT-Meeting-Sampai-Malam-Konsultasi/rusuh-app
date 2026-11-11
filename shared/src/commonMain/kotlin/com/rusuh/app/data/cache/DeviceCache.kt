package com.rusuh.app.data.cache

import com.rusuh.app.data.storage.AppPreferences

/** Device identity cache: when the stored device token expires (epoch millis, server timeline). */
class DeviceCache(
    prefs: AppPreferences,
) : PrefsCache(prefs, PREFIX) {
    suspend fun getTokenExpiresAt(): Long? = read(KEY_EXPIRES_AT)?.toLongOrNull()

    suspend fun setTokenExpiresAt(epochMillis: Long) = write(KEY_EXPIRES_AT, epochMillis.toString())

    suspend fun clearTokenExpiresAt() = delete(KEY_EXPIRES_AT)

    private companion object {
        const val PREFIX = "device_"
        const val KEY_EXPIRES_AT = PREFIX + "token_expires_at"
    }
}
