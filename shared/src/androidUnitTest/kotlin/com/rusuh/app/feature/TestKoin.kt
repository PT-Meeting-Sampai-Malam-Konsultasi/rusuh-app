package com.rusuh.app.feature

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rusuh.app.AppLanguageState
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

internal fun startScreenTestKoin() {
    stopKoin()
    startKoin {
        modules(module { single { AppLanguageState { error("Preferences are not used by screen tests") } } })
    }
}

internal fun initializeComposeResourceContext() {
    val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
    provider.getDeclaredField("ANDROID_CONTEXT").apply {
        isAccessible = true
        set(null, ApplicationProvider.getApplicationContext<Context>())
    }
}
