package com.rusuh.app.android

import android.app.Application
import com.rusuh.app.di.initKoinForBuild
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class RusuhApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Mock or real host: NetworkConfig.MOCK_API in the shared module (debug only; release is always real).
        initKoinForBuild(
            isDebug = BuildConfig.DEBUG,
            backendModeName = BuildConfig.BACKEND_MODE,
            mockApiOverride = BuildConfig.MOCK_API_OVERRIDE.toBooleanStrictOrNull(),
        ) {
            androidLogger()
            androidContext(this@RusuhApplication)
        }
    }
}
