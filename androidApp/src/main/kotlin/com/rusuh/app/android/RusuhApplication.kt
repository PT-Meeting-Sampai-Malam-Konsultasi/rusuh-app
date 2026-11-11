package com.rusuh.app.android

import android.app.Application
import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class RusuhApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(AppEnvironment.fromName(BuildConfig.APP_ENVIRONMENT)) {
            androidLogger()
            androidContext(this@RusuhApplication)
        }
    }
}
