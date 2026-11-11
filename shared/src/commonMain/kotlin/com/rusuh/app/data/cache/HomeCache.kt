package com.rusuh.app.data.cache

import com.rusuh.app.data.storage.AppPreferences

/** Home module cache: last event list payload, for cache-first rendering. */
class HomeCache(
    prefs: AppPreferences,
) : PrefsCache(prefs, PREFIX) {
    suspend fun getEvents(): String? = read(KEY_EVENTS)

    suspend fun setEvents(json: String) = write(KEY_EVENTS, json)

    suspend fun clearEvents() = delete(KEY_EVENTS)

    private companion object {
        const val PREFIX = "home_"
        const val KEY_EVENTS = PREFIX + "events_json"
    }
}
