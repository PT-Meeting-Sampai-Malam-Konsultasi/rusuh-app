package com.rusuh.app.feature.consent

import com.rusuh.app.analytics.Analytics
import com.rusuh.app.analytics.AnalyticsConsent
import com.rusuh.app.analytics.AnalyticsConsentStore
import com.rusuh.app.mvi.MviViewModel

class ConsentViewModel(
    private val consentStore: AnalyticsConsentStore,
    private val analytics: Analytics,
) : MviViewModel<ConsentState, ConsentIntent, ConsentEffect>(ConsentState()) {
    override suspend fun reduce(intent: ConsentIntent) {
        when (intent) {
            ConsentIntent.Accept -> {
                consentStore.set(AnalyticsConsent.Granted)
                analytics.setCollectionEnabled(true)
                sendEffect(ConsentEffect.Done)
            }
            ConsentIntent.Decline -> {
                consentStore.set(AnalyticsConsent.Denied)
                analytics.setCollectionEnabled(false)
                sendEffect(ConsentEffect.Done)
            }
        }
    }
}
