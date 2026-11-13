package com.rusuh.app.analytics

import com.rusuh.app.data.storage.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists the user's analytics consent choice. */
class AnalyticsConsentStore(
    private val prefs: AppPreferences,
) {
    private val _consent = MutableStateFlow(AnalyticsConsent.Unknown)
    private val _loaded = MutableStateFlow(false)

    val consent: StateFlow<AnalyticsConsent> = _consent.asStateFlow()
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() {
        _consent.value = runCatching { prefs.get(KEY)?.toConsentOrNull() }.getOrNull() ?: AnalyticsConsent.Unknown
        _loaded.value = true
    }

    suspend fun set(value: AnalyticsConsent) {
        prefs.set(KEY, value.name)
        _consent.value = value
        _loaded.value = true
    }

    private fun String.toConsentOrNull(): AnalyticsConsent? =
        AnalyticsConsent.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }

    private companion object {
        const val KEY = "analytics_consent"
    }
}
