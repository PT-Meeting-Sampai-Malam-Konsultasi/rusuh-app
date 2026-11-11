package com.rusuh.app.di

import com.rusuh.app.data.network.AppEnvironment
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Starts Koin with all shared modules for [environment], which each platform entry point derives from its
 * build type (no default: a release build must never fall back to the mock).
 * [backendMode] is the `useStubs` switch: by default Mock runs on [BackendMode.Stub] and the other environments on
 * [BackendMode.Real] (see docs/data-layer.md); entry points pass an explicit mode to override. Production with
 * [BackendMode.Stub] is rejected.
 * Platform entry points may pass [appDeclaration] (e.g. androidContext on Android).
 */
fun initKoin(
    environment: AppEnvironment,
    backendMode: BackendMode = BackendMode.defaultFor(environment),
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication {
    BackendMode.verify(environment, backendMode)
    return startKoin {
        appDeclaration()
        modules(module { single { environment } })
        modules(sharedModules(backendMode))
    }
}
