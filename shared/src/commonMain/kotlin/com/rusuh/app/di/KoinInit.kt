package com.rusuh.app.di

import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.data.network.NetworkConfig
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

/**
 * Starts Koin the way a platform build type asks for it: [isDebug] is the build type, [backendModeName] an optional
 * debug-only override (`"Stub"` or `"Real"`). The mock-or-real choice is `NetworkConfig.MOCK_API`, applied to debug
 * builds only; a release build always starts on Production with real HTTP (see [NetworkConfig.startup]).
 */
fun initKoinForBuild(
    isDebug: Boolean,
    backendModeName: String? = null,
    mockApiOverride: Boolean? = null,
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication {
    val startup = NetworkConfig.startup(isDebug, backendModeName, mockApiOverride ?: NetworkConfig.MOCK_API)
    return initKoin(startup.environment, startup.backendMode, appDeclaration)
}
