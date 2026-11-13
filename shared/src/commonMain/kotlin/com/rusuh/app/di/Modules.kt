package com.rusuh.app.di

import com.rusuh.app.analytics.Analytics
import com.rusuh.app.analytics.NoOpAnalytics
import com.rusuh.app.crash.CrashReporter
import com.rusuh.app.crash.NoOpCrashReporter
import org.koin.core.module.Module

/** Aggregates every Koin module for [backendMode]. Add new feature modules to this list. */
fun sharedModules(
    backendMode: BackendMode,
    platformAnalytics: () -> Analytics = { NoOpAnalytics },
    platformCrashReporter: () -> CrashReporter = { NoOpCrashReporter },
): List<Module> =
    // dataModules after coreModule: Stub mode overrides its HttpClient.
    listOf(coreModule()) +
        dataModules(backendMode) +
        listOf(
            analyticsModule(backendMode, platformAnalytics),
            crashReporterModule(backendMode, platformCrashReporter),
            appModule,
            platformModule,
            storageModule,
        )
