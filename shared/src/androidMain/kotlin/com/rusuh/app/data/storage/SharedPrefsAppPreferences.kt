package com.rusuh.app.data.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [AppPreferences] on a private SharedPreferences file. */
class SharedPrefsAppPreferences(
    context: Context,
) : AppPreferences {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) { prefs.getString(key, null) }

    override suspend fun set(
        key: String,
        value: String,
    ) {
        withContext(Dispatchers.IO) { prefs.edit().putString(key, value).apply() }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) { prefs.edit().remove(key).apply() }
    }

    private companion object {
        const val FILE_NAME = "rusuh_app_preferences"
    }
}
