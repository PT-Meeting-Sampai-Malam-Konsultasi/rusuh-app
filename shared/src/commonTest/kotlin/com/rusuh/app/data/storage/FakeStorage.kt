package com.rusuh.app.data.storage

import kotlinx.coroutines.yield

class FakeSecureStore : SecureStore {
    val entries = mutableMapOf<String, String>()

    /** Keys (or prefixes for [removeWhere]) whose removal throws. */
    val failingRemovals = mutableSetOf<String>()

    /** Yield inside writes so concurrency tests can interleave coroutines. */
    var yieldOnWrite = false

    override suspend fun getString(key: String): String? = entries[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        if (yieldOnWrite) yield()
        entries[key] = value
    }

    override suspend fun remove(key: String) {
        check(key !in failingRemovals) { "remove failed" }
        entries.remove(key)
    }

    override suspend fun removeWhere(prefix: String) {
        check(prefix !in failingRemovals) { "removeWhere failed" }
        entries.keys.removeAll { it.startsWith(prefix) }
    }

    override suspend fun clear() {
        entries.clear()
    }
}

class FakeAppPreferences : AppPreferences {
    val entries = mutableMapOf<String, String>()

    override suspend fun get(key: String): String? = entries[key]

    override suspend fun set(
        key: String,
        value: String,
    ) {
        entries[key] = value
    }

    override suspend fun remove(key: String) {
        entries.remove(key)
    }
}
