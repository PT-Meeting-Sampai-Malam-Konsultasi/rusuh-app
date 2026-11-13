package com.rusuh.app.data.network

import com.rusuh.app.di.BackendMode
import com.rusuh.app.di.initKoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class NetworkConfigTest {
    // ─────────────────────────────────────────────────────────────────────────
    // Staging flavor (allowDebugOptions = true)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun theMockServerIsOffByDefault() {
        assertFalse(NetworkConfig.MOCK_API)
        assertEquals(AppEnvironment.Staging, NetworkConfig.startup(isDebug = true).environment)
    }

    @Test
    fun stagingDebugWithMockApiTalksHttpToMockoon() {
        val startup =
            NetworkConfig.startup(
                isDebug = true,
                allowDebugOptions = true,
                environmentName = "Staging",
                mockApi = true,
            )

        assertEquals(AppEnvironment.Mock, startup.environment)
        assertEquals(BackendMode.Real, startup.backendMode)
        assertEquals(platformMockBaseUrl, ApiConfig.forEnvironment(startup.environment).baseUrl)
    }

    @Test
    fun stagingDebugWithoutMockApiTalksToTheRealHost() {
        val startup =
            NetworkConfig.startup(
                isDebug = true,
                allowDebugOptions = true,
                environmentName = "Staging",
                mockApi = false,
            )

        assertEquals(AppEnvironment.Staging, startup.environment)
        assertEquals(BackendMode.Real, startup.backendMode)
        assertEquals("https://api.rusuh-tiket.web.id/v1", ApiConfig.forEnvironment(startup.environment).baseUrl)
    }

    @Test
    fun stagingReleaseIsAlwaysProductionReal() {
        listOf(true, false).forEach { mockApi ->
            listOf(null, "", "Real", "Stub").forEach { override ->
                val startup =
                    NetworkConfig.startup(
                        isDebug = false,
                        allowDebugOptions = true,
                        environmentName = "Staging",
                        backendModeOverride = override,
                        mockApi = mockApi,
                    )

                assertEquals(AppEnvironment.Production, startup.environment, "mockApi=$mockApi override=$override")
                assertEquals(BackendMode.Real, startup.backendMode, "mockApi=$mockApi override=$override")
            }
        }
    }

    @Test
    fun stagingDebugStubModeIsAnOptIn() {
        listOf(true, false).forEach { mockApi ->
            val startup =
                NetworkConfig.startup(
                    isDebug = true,
                    allowDebugOptions = true,
                    environmentName = "Staging",
                    backendModeOverride = "Stub",
                    mockApi = mockApi,
                )

            assertEquals(BackendMode.Stub, startup.backendMode)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Production flavor (allowDebugOptions = false)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun productionDebugIsAlwaysProductionReal() {
        listOf(true, false).forEach { mockApi ->
            listOf(null, "", "Real", "Stub", "stub", "Mock", "garbage").forEach { override ->
                val startup =
                    NetworkConfig.startup(
                        isDebug = true,
                        allowDebugOptions = false,
                        environmentName = "Production",
                        backendModeOverride = override,
                        mockApi = mockApi,
                    )

                assertEquals(
                    AppEnvironment.Production,
                    startup.environment,
                    "production debug with mockApi=$mockApi override=$override must be Production",
                )
                assertEquals(
                    BackendMode.Real,
                    startup.backendMode,
                    "production debug with mockApi=$mockApi override=$override must be Real",
                )
            }
        }
    }

    @Test
    fun productionReleaseIsAlwaysProductionReal() {
        listOf(true, false).forEach { mockApi ->
            listOf(null, "", "Real", "Stub").forEach { override ->
                val startup =
                    NetworkConfig.startup(
                        isDebug = false,
                        allowDebugOptions = false,
                        environmentName = "Production",
                        backendModeOverride = override,
                        mockApi = mockApi,
                    )

                assertEquals(AppEnvironment.Production, startup.environment, "mockApi=$mockApi override=$override")
                assertEquals(BackendMode.Real, startup.backendMode, "mockApi=$mockApi override=$override")
            }
        }
    }

    @Test
    fun productionFlavorCannotEnableStubEvenInDebug() {
        // Even if someone tried to pass -PbackendMode=Stub on a production debug build,
        // allowDebugOptions = false means it is ignored.
        val startup =
            NetworkConfig.startup(
                isDebug = true,
                allowDebugOptions = false,
                environmentName = "Production",
                backendModeOverride = "Stub",
                mockApi = true,
            )

        assertEquals(AppEnvironment.Production, startup.environment)
        assertEquals(BackendMode.Real, startup.backendMode)
    }

    @Test
    fun productionFlavorCannotEnableMockApiEvenInDebug() {
        val startup =
            NetworkConfig.startup(
                isDebug = true,
                allowDebugOptions = false,
                environmentName = "Production",
                backendModeOverride = null,
                mockApi = true,
            )

        assertEquals(AppEnvironment.Production, startup.environment)
        assertEquals(BackendMode.Real, startup.backendMode)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy tests (using deprecated 3-arg startup for backward compatibility)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun legacyDebugWithMockApiTalksHttpToMockoon() {
        @Suppress("DEPRECATION")
        val startup = NetworkConfig.startup(isDebug = true, mockApi = true)

        assertEquals(AppEnvironment.Mock, startup.environment)
        assertEquals(BackendMode.Real, startup.backendMode)
        assertEquals(platformMockBaseUrl, ApiConfig.forEnvironment(startup.environment).baseUrl)
    }

    @Test
    fun legacyDebugWithoutMockApiTalksToTheRealHost() {
        @Suppress("DEPRECATION")
        val startup = NetworkConfig.startup(isDebug = true, mockApi = false)

        assertEquals(AppEnvironment.Staging, startup.environment)
        assertEquals(BackendMode.Real, startup.backendMode)
        assertEquals("https://api.rusuh-tiket.web.id/v1", ApiConfig.forEnvironment(startup.environment).baseUrl)
    }

    @Test
    fun legacyReleaseBuildNeverSelectsTheMockWhateverTheFlagOrOverrideSays() {
        listOf(true, false).forEach { mockApi ->
            listOf(null, "", "Real", "Stub", "stub", "Mock", "garbage").forEach { override ->
                @Suppress("DEPRECATION")
                val startup = NetworkConfig.startup(isDebug = false, backendModeOverride = override, mockApi = mockApi)

                assertEquals(AppEnvironment.Production, startup.environment, "mockApi=$mockApi override=$override")
                assertEquals(BackendMode.Real, startup.backendMode, "mockApi=$mockApi override=$override")
                assertEquals(
                    "https://api.rusuh-tiket.web.id/v1",
                    ApiConfig.forEnvironment(startup.environment).baseUrl,
                )
            }
        }
    }

    @Test
    fun legacyStubModeIsADebugOnlyOptIn() {
        listOf(true, false).forEach { mockApi ->
            @Suppress("DEPRECATION")
            val startup = NetworkConfig.startup(isDebug = true, backendModeOverride = "Stub", mockApi = mockApi)

            assertEquals(BackendMode.Stub, startup.backendMode)
        }
        @Suppress("DEPRECATION")
        assertEquals(BackendMode.Real, NetworkConfig.startup(isDebug = true, backendModeOverride = " ").backendMode)
    }

    @Test
    fun legacyUnknownDebugOverrideFailsLoudly() {
        assertFailsWith<IllegalArgumentException> {
            @Suppress("DEPRECATION")
            NetworkConfig.startup(
                isDebug = true,
                backendModeOverride = "fake",
            )
        }
    }

    @Test
    fun legacyMockIsNeverAProductionEnvironment() {
        // The only way to reach AppEnvironment.Mock is a debug build with the flag on.
        val mockStartups =
            listOf(true, false).flatMap { debug ->
                @Suppress("DEPRECATION")
                listOf(true, false).map { mock -> NetworkConfig.startup(debug, null, mock) }
            }.filter { it.environment == AppEnvironment.Mock }

        assertEquals(1, mockStartups.size)
    }

    @Test
    fun productionStillRejectsStubEvenWhenAskedDirectly() {
        assertFailsWith<IllegalArgumentException> { initKoin(AppEnvironment.Production, BackendMode.Stub) }
    }
}
