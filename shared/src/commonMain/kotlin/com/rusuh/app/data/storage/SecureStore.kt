package com.rusuh.app.data.storage

/**
 * Encrypted key-value storage for secrets (refresh, device, queue and admission tokens).
 * Android: AES-GCM key held in the AndroidKeyStore. iOS: Keychain generic password items.
 * Values never leave the device and must never be logged.
 */
interface SecureStore {
    suspend fun getString(key: String): String?

    suspend fun putString(
        key: String,
        value: String,
    )

    suspend fun remove(key: String)

    /** Removes every entry whose key starts with [prefix]. A missing match is not an error. */
    suspend fun removeWhere(prefix: String)

    /** Removes every entry owned by this store. */
    suspend fun clear()
}
