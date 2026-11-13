package com.rusuh.app.di

import com.rusuh.app.analytics.Analytics
import com.rusuh.app.analytics.AnalyticsConsentStore
import com.rusuh.app.analytics.ConsentAwareAnalytics
import com.rusuh.app.analytics.NoOpAnalytics
import com.rusuh.app.data.network.AppEnvironment
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Analytics wiring. [platformAnalytics] provides the platform-specific implementation (Firebase on Android, no-op
 * on iOS until the Firebase pod is added). The consent store and consent-aware decorator are shared.
 */
fun analyticsModule(
    backendMode: BackendMode,
    platformAnalytics: () -> Analytics = { NoOpAnalytics },
): Module =
    module {
        single { AnalyticsConsentStore(get()) }

        single<Analytics> {
            val environment: AppEnvironment = get()
            val consentStore: AnalyticsConsentStore = get()
            val delegate = platformAnalytics()
            ConsentAwareAnalytics(delegate, consentStore, environment, backendMode)
        }
    }
