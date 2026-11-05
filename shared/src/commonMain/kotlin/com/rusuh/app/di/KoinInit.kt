package com.rusuh.app.di

import org.koin.core.context.startKoin
import org.koin.core.KoinApplication
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts Koin with all shared modules.
 * Platform entry points may pass [appDeclaration] (e.g. androidContext on Android).
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        appDeclaration()
        modules(sharedModules)
    }
