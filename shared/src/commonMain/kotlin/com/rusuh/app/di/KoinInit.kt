package com.rusuh.app.di

import com.rusuh.app.data.network.AppEnvironment
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Starts Koin with all shared modules for [environment], which each platform entry point derives from its
 * build type (no default: a release build must never fall back to the mock).
 * Platform entry points may pass [appDeclaration] (e.g. androidContext on Android).
 */
fun initKoin(
    environment: AppEnvironment,
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication =
    startKoin {
        appDeclaration()
        modules(module { single { environment } })
        modules(sharedModules)
    }
