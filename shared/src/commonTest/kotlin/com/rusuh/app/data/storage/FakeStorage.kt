package com.rusuh.app.data.storage

class FakeSecureStore : SecureStore {
    val entries = mutableMapOf<String, String>()

    override suspend fun getString(key: String): String? = entries[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        entries[key] = value
    }

    override suspend fun remove(key: String) {
        entries.remove(key)
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
