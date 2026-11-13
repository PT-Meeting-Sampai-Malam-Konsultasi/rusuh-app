package com.rusuh.app.data.network

import com.rusuh.app.di.BackendMode

/**
 * The ONE place to flip between the local mock server and the real backend in a debug build.
 *
 * Debug Android can override [MOCK_API] with `-PmockApi=false` (or true). Otherwise edit [MOCK_API] and rebuild:
 * - `true`: HTTP to the local Mockoon server (`workspace-local/mockoon`). Android emulator
 *   `http://10.0.2.2:3000`, iOS simulator `http://localhost:3000`; for a physical Android device run
 *   `adb reverse tcp:3000 tcp:3000` and point the app at `http://localhost:3000`.
 * - `false`: HTTP to the real backend (`https://api.rusuh-tiket.web.id/v1`, see [ApiConfig]).
 *
 * The flag applies to DEBUG builds of the STAGING flavor only. A release build or the production flavor ALWAYS
 * talks to the real host ([AppEnvironment.Production]) on [BackendMode.Real], whatever [MOCK_API] says; [startup]
 * enforces it and Gradle guards (`verifyReleaseNeverUsesMockApi`, `verifyProductionFlavorHasNoDebugOptions`) check
 * the generated build config. The in-process [BackendMode.Stub] is a separate debug-only opt-in
 * (`-PbackendMode=Stub` on Android), for offline demos.
 */
object NetworkConfig {
    /**
     * `true` = local Mockoon, `false` = the real host.
     * Debug builds of the staging flavor only; see the class documentation.
     */
    const val MOCK_API: Boolean = true

    /** What the platform entry point starts the app with. */
    data class Startup(
        val environment: AppEnvironment,
        val backendMode: BackendMode,
    )

    /**
     * The environment and backend mode for a build. The flavor determines the environment:
     * - `"Staging"` flavor: [isDebug] true allows [mockApi] and [backendModeOverride] (mock, stub, live staging).
     * - `"Production"` flavor: always [AppEnvironment.Production] on [BackendMode.Real], no debug options.
     * - Release builds (any flavor): always [AppEnvironment.Production] on [BackendMode.Real].
     *
     * [allowDebugOptions] comes from `BuildConfig.ALLOW_DEBUG_OPTIONS` (staging = true, production = false).
     * When false, [mockApi] and [backendModeOverride] are ignored.
     *
     * [environmentName] comes from `BuildConfig.ENVIRONMENT` ("Staging" or "Production").
     */
    fun startup(
        isDebug: Boolean,
        allowDebugOptions: Boolean,
        @Suppress("UnusedParameter") environmentName: String,
        backendModeOverride: String? = null,
        mockApi: Boolean = MOCK_API,
    ): Startup =
        if (isDebug && allowDebugOptions) {
            // Staging debug: follow the mock/stub switches.
            val environment = if (mockApi) AppEnvironment.Mock else AppEnvironment.Staging
            Startup(environment, BackendMode.resolve(environment, backendModeOverride))
        } else {
            // Production flavor (any build type) or release build: always Production + Real.
            Startup(AppEnvironment.Production, BackendMode.Real)
        }

    /**
     * Legacy overload for iOS and tests: defaults [allowDebugOptions] to true and [environmentName] to empty.
     * Use the full [startup] for Android.
     */
    @Deprecated(
        "Use startup(isDebug, allowDebugOptions, environmentName, ...) for flavor-aware behavior",
        ReplaceWith("startup(isDebug, true, \"\", backendModeOverride, mockApi)"),
    )
    fun startup(
        isDebug: Boolean,
        backendModeOverride: String? = null,
        mockApi: Boolean = MOCK_API,
    ): Startup = startup(isDebug, allowDebugOptions = true, environmentName = "", backendModeOverride, mockApi)
}
