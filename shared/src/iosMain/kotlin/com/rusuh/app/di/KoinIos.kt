package com.rusuh.app.di

/**
 * Called from Swift (iOSApp.init) as `KoinIosKt.doInitKoin(isDebug:backendModeName:useMockServer:)`.
 * [isDebug] is the Swift `DEBUG` build flag; [backendModeName] an optional debug-only override (`"Stub"` or
 * `"Real"`, null or blank = default). [useMockServer] points a debug build at the local Mockoon server instead of
 * the live host; it defaults to false, so iOS talks to the real API. A release build always uses the real host
 * and ignores both, see `NetworkConfig.startup`.
 *
 * iOS flavor/scheme work is a follow-up; for now iOS behaves like the staging flavor (allowDebugOptions = true).
 */
fun doInitKoin(
    isDebug: Boolean,
    backendModeName: String?,
    useMockServer: Boolean,
) {
    // iOS has no flavors yet, so allowDebugOptions = true (like staging) and environmentName = "" (ignored).
    initKoinForBuild(
        isDebug = isDebug,
        allowDebugOptions = true,
        environmentName = "",
        backendModeName = backendModeName,
        mockApiOverride = useMockServer,
    )
}
