package com.rusuh.app.di

import org.koin.core.module.Module

/** Aggregates every Koin module for [backendMode]. Add new feature modules to this list. */
fun sharedModules(backendMode: BackendMode): List<Module> =
    // dataModules after coreModule: Stub mode overrides its HttpClient.
    listOf(coreModule()) + dataModules(backendMode) + listOf(appModule, platformModule, storageModule)
