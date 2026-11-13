package com.rusuh.app.android

import android.app.Application
import com.rusuh.app.android.analytics.FirebaseAnalyticsImpl
import com.rusuh.app.di.initKoinForBuild
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class RusuhApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoinForBuild(
            isDebug = BuildConfig.DEBUG,
            allowDebugOptions = BuildConfig.ALLOW_DEBUG_OPTIONS,
            environmentName = BuildConfig.ENVIRONMENT,
            backendModeName = BuildConfig.BACKEND_MODE,
            mockApiOverride = BuildConfig.MOCK_API_OVERRIDE.toBooleanStrictOrNull(),
            platformAnalytics = { FirebaseAnalyticsImpl(this) },
        ) {
            androidLogger()
            androidContext(this@RusuhApplication)
        }
    }
}
