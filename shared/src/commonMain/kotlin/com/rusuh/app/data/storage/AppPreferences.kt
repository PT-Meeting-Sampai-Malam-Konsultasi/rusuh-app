package com.rusuh.app.data.storage

/**
 * Non-sensitive key-value storage (cached JSON, flags). Never put tokens here: use [SecureStore].
 * Booleans are stored as "true"/"false"; each module owns its keys through a `*Cache` class.
 */
interface AppPreferences {
    suspend fun get(key: String): String?

    suspend fun set(
        key: String,
        value: String,
    )

    suspend fun remove(key: String)
}
