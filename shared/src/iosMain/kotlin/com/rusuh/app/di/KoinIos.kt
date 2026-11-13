package com.rusuh.app.di

/**
 * Called from Swift (iOSApp.init) as `KoinIosKt.doInitKoin(...)`.
 *
 * [isDebug] is the Swift `DEBUG` build flag.
 * [environmentName] comes from the `RusuhEnvironment` Info.plist key ("Staging" or "Production").
 * [allowDebugOptions] comes from the `RusuhAllowDebugOptions` Info.plist key (YES for Staging, NO for Production).
 * [backendModeName] is an optional debug-only override (`"Stub"` or `"Real"`, null or blank = default).
 * [useMockServer] points a debug build at the local Mockoon server instead of the live host.
 *
 * Production builds (allowDebugOptions = false) ignore [backendModeName] and [useMockServer].
 * Release builds always use the production API.
 */
fun doInitKoin(
    isDebug: Boolean,
    environmentName: String,
    allowDebugOptions: Boolean,
    backendModeName: String?,
    useMockServer: Boolean,
) {
    initKoinForBuild(
        isDebug = isDebug,
        allowDebugOptions = allowDebugOptions,
        environmentName = environmentName,
        backendModeName = backendModeName,
        mockApiOverride = useMockServer,
    )
}
