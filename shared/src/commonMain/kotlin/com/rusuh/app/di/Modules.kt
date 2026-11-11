package com.rusuh.app.di

import org.koin.core.module.Module

/** Aggregates every Koin module. Add new feature modules to this list. */
val sharedModules: List<Module>
    get() =
        listOf(
            coreModule(),
            appModule,
            platformModule,
        )
