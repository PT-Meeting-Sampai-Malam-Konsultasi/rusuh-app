package com.rusuh.app.data.cache

import com.rusuh.app.data.storage.AppPreferences

/**
 * Base for module caches. A subclass declares its prefix (`home_`, `queue_`, `book_`, `tix_`, `auth_`) and
 * owns every key string under it; [read]/[write]/[delete] reject keys outside the prefix.
 */
abstract class PrefsCache(
    private val prefs: AppPreferences,
    private val prefix: String,
) {
    init {
        require(prefix.isNotEmpty()) { "Cache prefix must not be empty" }
    }

    protected suspend fun read(key: String): String? = prefs.get(checked(key))

    protected suspend fun write(
        key: String,
        value: String,
    ) = prefs.set(checked(key), value)

    protected suspend fun delete(key: String) = prefs.remove(checked(key))

    protected suspend fun readBoolean(key: String): Boolean? = read(key)?.toBooleanStrictOrNull()

    protected suspend fun writeBoolean(
        key: String,
        value: Boolean,
    ) = write(key, value.toString())

    private fun checked(key: String): String {
        require(
            key.startsWith(prefix) && key.length > prefix.length,
        ) { "Cache key must start with $prefix and name a value" }
        return key
    }
}
