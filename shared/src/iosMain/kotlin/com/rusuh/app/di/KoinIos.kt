package com.rusuh.app.di

/**
 * Called from Swift (iOSApp.init) as `KoinIosKt.doInitKoin(isDebug:backendModeName:)`. [isDebug] is the Swift `DEBUG`
 * build flag; [backendModeName] an optional debug-only override (`"Stub"` or `"Real"`, null or blank = default).
 * Debug builds follow `NetworkConfig.MOCK_API` (Mockoon or the real host); a release build always uses the real host
 * and ignores the override, see `NetworkConfig.startup`.
 */
fun doInitKoin(
    isDebug: Boolean,
    backendModeName: String?,
) {
    initKoinForBuild(isDebug, backendModeName)
}
