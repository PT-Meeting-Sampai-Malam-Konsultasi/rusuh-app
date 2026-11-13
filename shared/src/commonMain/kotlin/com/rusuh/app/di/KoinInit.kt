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
 * Starts Koin the way a platform build type asks for it, with flavor support.
 *
 * Android passes:
 * - [isDebug]: from `BuildConfig.DEBUG`
 * - [allowDebugOptions]: from `BuildConfig.ALLOW_DEBUG_OPTIONS` (staging = true, production = false)
 * - [environmentName]: from `BuildConfig.ENVIRONMENT` ("Staging" or "Production")
 * - [backendModeName]: from `BuildConfig.BACKEND_MODE` (optional debug override)
 * - [mockApiOverride]: from `BuildConfig.MOCK_API_OVERRIDE` (optional debug override)
 *
 * The production flavor (ALLOW_DEBUG_OPTIONS = false) always uses Production + Real, ignoring any overrides.
 *
 * iOS has no flavors yet: [allowDebugOptions] defaults to true (behaves as staging), [environmentName] defaults
 * to empty (ignored when allowDebugOptions is true). iOS flavor work is a follow-up.
 */
@Suppress("LongParameterList") // Entry point bridging platform build config to Koin startup.
fun initKoinForBuild(
    isDebug: Boolean,
    allowDebugOptions: Boolean = true,
    environmentName: String = "",
    backendModeName: String? = null,
    mockApiOverride: Boolean? = null,
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication {
    val startup =
        NetworkConfig.startup(
            isDebug = isDebug,
            allowDebugOptions = allowDebugOptions,
            environmentName = environmentName,
            backendModeOverride = backendModeName,
            mockApi = mockApiOverride ?: NetworkConfig.MOCK_API,
        )
    return initKoin(startup.environment, startup.backendMode, appDeclaration)
}
