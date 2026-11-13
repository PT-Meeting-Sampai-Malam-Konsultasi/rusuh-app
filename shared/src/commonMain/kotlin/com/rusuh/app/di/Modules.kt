package com.rusuh.app.di

import com.rusuh.app.analytics.Analytics
import com.rusuh.app.analytics.NoOpAnalytics
import org.koin.core.module.Module

/** Aggregates every Koin module for [backendMode]. Add new feature modules to this list. */
fun sharedModules(
    backendMode: BackendMode,
    platformAnalytics: () -> Analytics = { NoOpAnalytics },
): List<Module> =
    // dataModules after coreModule: Stub mode overrides its HttpClient.
    listOf(coreModule()) +
        dataModules(backendMode) +
        listOf(analyticsModule(backendMode, platformAnalytics), appModule, platformModule, storageModule)
