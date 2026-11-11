package com.rusuh.app.data.network

import com.rusuh.app.di.BackendMode

/**
 * The ONE place to flip between the local mock server and the real backend in a debug build.
 *
 * Edit [MOCK_API] and rebuild:
 * - `true`: HTTP to the local Mockoon server (`workspace-local/mockoon`). Android emulator
 *   `http://10.0.2.2:3000`, iOS simulator `http://localhost:3000`; for a physical Android device run
 *   `adb reverse tcp:3000 tcp:3000` and point the app at `http://localhost:3000`.
 * - `false`: HTTP to the real backend (`https://api.rusuh-tiket.web.id`, see [ApiConfig]).
 *
 * The flag applies to DEBUG builds only. A release build ALWAYS talks to the real host ([AppEnvironment.Production])
 * on [BackendMode.Real], whatever [MOCK_API] says; [startup] enforces it and a Gradle guard
 * (`verifyReleaseNeverUsesMockApi`) checks the release build config. The in-process [BackendMode.Stub] is a separate
 * debug-only opt-in (`-PbackendMode=Stub` on Android), for offline demos.
 */
object NetworkConfig {
    /** `true` = local Mockoon, `false` = the real host. Debug builds only; see the class documentation. */
    const val MOCK_API: Boolean = true

    /** What the platform entry point starts the app with. */
    data class Startup(
        val environment: AppEnvironment,
        val backendMode: BackendMode,
    )

    /**
     * The environment and backend mode for a build. [isDebug] comes from the platform build type. In a release build
     * ([isDebug] false) the answer is always Production on [BackendMode.Real]: [mockApi] and [backendModeOverride]
     * are ignored, so a release can never select the mock or stub data. In a debug build [mockApi] picks
     * [AppEnvironment.Mock] (true) or [AppEnvironment.Staging] (false), both on [BackendMode.Real] unless
     * [backendModeOverride] (null or blank keeps the default) names another mode.
     */
    fun startup(
        isDebug: Boolean,
        backendModeOverride: String? = null,
        mockApi: Boolean = MOCK_API,
    ): Startup =
        if (isDebug) {
            val environment = if (mockApi) AppEnvironment.Mock else AppEnvironment.Staging
            Startup(environment, BackendMode.resolve(environment, backendModeOverride))
        } else {
            Startup(AppEnvironment.Production, BackendMode.Real)
        }
}
