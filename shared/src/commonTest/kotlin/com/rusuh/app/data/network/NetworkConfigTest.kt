package com.rusuh.app.data.network

import com.rusuh.app.di.BackendMode
import com.rusuh.app.di.initKoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NetworkConfigTest {
    @Test
    fun debugWithMockApiTalksHttpToMockoon() {
        val startup = NetworkConfig.startup(isDebug = true, mockApi = true)

        assertEquals(AppEnvironment.Mock, startup.environment)
        assertEquals(BackendMode.Real, startup.backendMode)
        assertEquals(platformMockBaseUrl, ApiConfig.forEnvironment(startup.environment).baseUrl)
    }

    @Test
    fun debugWithoutMockApiTalksToTheRealHost() {
        val startup = NetworkConfig.startup(isDebug = true, mockApi = false)

        assertEquals(AppEnvironment.Staging, startup.environment)
        assertEquals(BackendMode.Real, startup.backendMode)
        assertEquals("https://api.rusuh-tiket.web.id/v1", ApiConfig.forEnvironment(startup.environment).baseUrl)
    }

    @Test
    fun aReleaseBuildNeverSelectsTheMockWhateverTheFlagOrOverrideSays() {
        listOf(true, false).forEach { mockApi ->
            listOf(null, "", "Real", "Stub", "stub", "Mock", "garbage").forEach { override ->
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
    fun theStubModeIsADebugOnlyOptIn() {
        listOf(true, false).forEach { mockApi ->
            val startup = NetworkConfig.startup(isDebug = true, backendModeOverride = "Stub", mockApi = mockApi)

            assertEquals(BackendMode.Stub, startup.backendMode)
        }
        assertEquals(BackendMode.Real, NetworkConfig.startup(isDebug = true, backendModeOverride = " ").backendMode)
    }

    @Test
    fun anUnknownDebugOverrideFailsLoudly() {
        assertFailsWith<IllegalArgumentException> {
            NetworkConfig.startup(
                isDebug = true,
                backendModeOverride = "fake",
            )
        }
    }

    @Test
    fun theMockIsNeverAProductionEnvironment() {
        // The only way to reach AppEnvironment.Mock is a debug build with the flag on.
        val mockStartups =
            listOf(true, false).flatMap { debug ->
                listOf(true, false).map { mock -> NetworkConfig.startup(debug, null, mock) }
            }.filter { it.environment == AppEnvironment.Mock }

        assertEquals(1, mockStartups.size)
    }

    @Test
    fun productionStillRejectsStubEvenWhenAskedDirectly() {
        assertFailsWith<IllegalArgumentException> { initKoin(AppEnvironment.Production, BackendMode.Stub) }
    }
}
