package com.rusuh.app.analytics

import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.di.BackendMode

/**
 * Decorator that forwards analytics calls only when:
 * - Consent is [AnalyticsConsent.Granted]
 * - The environment is not [AppEnvironment.Mock]
 * - The backend mode is not [BackendMode.Stub]
 */
class ConsentAwareAnalytics(
    private val delegate: Analytics,
    private val consentStore: AnalyticsConsentStore,
    private val environment: AppEnvironment,
    private val backendMode: BackendMode,
) : Analytics {
    private val isAnalyticsAllowed: Boolean
        get() = environment != AppEnvironment.Mock && backendMode != BackendMode.Stub

    private val isEnabled: Boolean
        get() = isAnalyticsAllowed && consentStore.consent.value == AnalyticsConsent.Granted

    override fun log(
        event: String,
        params: Map<String, Any>,
    ) {
        if (isEnabled) delegate.log(event, params)
    }

    override fun setUserId(id: String?) {
        if (isAnalyticsAllowed) {
            if (consentStore.consent.value == AnalyticsConsent.Granted) {
                delegate.setUserId(id)
            } else {
                delegate.setUserId(null)
            }
        }
    }

    override fun setUserProperty(
        name: String,
        value: String?,
    ) {
        if (isEnabled) delegate.setUserProperty(name, value)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        if (isAnalyticsAllowed) {
            delegate.setCollectionEnabled(enabled && consentStore.consent.value == AnalyticsConsent.Granted)
        }
    }
}
