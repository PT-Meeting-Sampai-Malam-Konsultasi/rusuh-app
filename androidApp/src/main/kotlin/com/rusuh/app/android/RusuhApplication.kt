package com.rusuh.app.android

import android.app.Application
import com.rusuh.app.di.initKoinForBuild
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class RusuhApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Flavor-aware initialization: staging allows mock/stub in debug, production is always Production + Real.
        initKoinForBuild(
            isDebug = BuildConfig.DEBUG,
            allowDebugOptions = BuildConfig.ALLOW_DEBUG_OPTIONS,
            environmentName = BuildConfig.ENVIRONMENT,
            backendModeName = BuildConfig.BACKEND_MODE,
            mockApiOverride = BuildConfig.MOCK_API_OVERRIDE.toBooleanStrictOrNull(),
        ) {
            androidLogger()
            androidContext(this@RusuhApplication)
        }
    }
}
