package com.rusuh.app.di

import com.rusuh.app.data.clock.MonotonicServerClock
import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.data.stub.StubEngine
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.repository.DetailRepository
import com.rusuh.app.domain.repository.MyQueuesRepository
import com.rusuh.app.feature.home.HomeRepository
import com.rusuh.app.feature.home.StubHomeRepository
import com.rusuh.app.feature.tickets.TicketsRepository
import com.rusuh.app.feature.waitingroom.domain.repository.WaitingRoomRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.koin.core.Koin
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataModuleTest {
    private fun koin(
        environment: AppEnvironment,
        mode: BackendMode,
    ): Koin =
        koinApplication {
            allowOverride(true)
            modules(
                module {
                    single { environment }
                    single<ServerClock> { MonotonicServerClock() }
                },
                coreModule(),
                dataModule(mode),
            )
        }.koin

    private val repositories: Map<String, KClass<*>> =
        mapOf(
            "HomeRepository" to HomeRepository::class,
            "WaitingRoomRepository" to WaitingRoomRepository::class,
            "TicketsRepository" to TicketsRepository::class,
            "DetailRepository" to DetailRepository::class,
            "MyQueuesRepository" to MyQueuesRepository::class,
        )

    @Test
    fun mockDefaultsToStubAndTheRestToReal() {
        assertEquals(BackendMode.Stub, BackendMode.defaultFor(AppEnvironment.Mock))
        assertEquals(BackendMode.Real, BackendMode.defaultFor(AppEnvironment.Staging))
        assertEquals(BackendMode.Real, BackendMode.defaultFor(AppEnvironment.Production))
    }

    @Test
    fun resolveAppliesTheOverrideAndTheDefault() {
        assertEquals(BackendMode.Stub, BackendMode.resolve(AppEnvironment.Mock))
        assertEquals(BackendMode.Stub, BackendMode.resolve(AppEnvironment.Mock, ""))
        assertEquals(BackendMode.Stub, BackendMode.resolve(AppEnvironment.Mock, null))
        assertEquals(BackendMode.Real, BackendMode.resolve(AppEnvironment.Mock, "real"))
        assertEquals(BackendMode.Stub, BackendMode.resolve(AppEnvironment.Staging, " Stub "))
    }

    @Test
    fun unknownModeNameFailsLoudly() {
        val failure = assertFailsWith<IllegalArgumentException> { BackendMode.resolve(AppEnvironment.Mock, "fake") }
        assertTrue("fake" in failure.message.orEmpty() && "Stub" in failure.message.orEmpty())
    }

    @Test
    fun productionNeverRunsOnStubs() {
        assertFailsWith<IllegalArgumentException> { BackendMode.resolve(AppEnvironment.Production, "Stub") }
        assertFailsWith<IllegalArgumentException> { initKoin(AppEnvironment.Production, BackendMode.Stub) }
    }

    @Test
    fun stubModeBindsEveryRepository() {
        val koin = koin(AppEnvironment.Mock, BackendMode.Stub)

        repositories.values.forEach { type -> koin.get<Any>(type) } // throws when a binding is missing or fails
        assertIs<StubHomeRepository>(koin.get<HomeRepository>())
        assertSame(koin.get<HomeRepository>(), koin.get<HomeRepository>())
    }

    @Test
    fun realModeFailsLoudlyForEveryRepositoryWithoutANetworkImplementation() {
        val koin = koin(AppEnvironment.Mock, BackendMode.Real)

        repositories.forEach { (name, type) ->
            val failure = assertFailsWith<Exception>(name) { koin.get<Any>(type) }
            val messages = generateSequence<Throwable>(failure) { it.cause }.mapNotNull { it.message }.toList()
            assertTrue(
                messages.any { "No real implementation of $name yet" in it && "BackendMode.Stub" in it },
                "$name: $messages",
            )
        }
    }

    @Test
    fun realModeDoesNotFallBackToAStub() {
        val koin = koin(AppEnvironment.Mock, BackendMode.Real)

        assertFailsWith<Exception> { koin.get<HomeRepository>() }
        assertFailsWith<Exception> { koin.get<HomeRepository>() } // still failing on a second resolution
    }

    @Test
    fun stubModeServesTheNamedApiClientsFromTheStubEngine() =
        runTest {
            val koin = koin(AppEnvironment.Mock, BackendMode.Stub)

            val events = koin.get<ApiClient>(ApiClientQualifier.Api).get<JsonObject>("/events")
            val queue = koin.get<ApiClient>(ApiClientQualifier.Queue).get<JsonObject>("/queue/evt_2026_gbk/frontier")

            assertIs<ApiResult.Success<JsonObject>>(events)
            assertIs<ApiResult.Success<JsonObject>>(queue)
            assertEquals(
                listOf("/events", "/queue/evt_2026_gbk/frontier"),
                koin.get<StubEngine>().requests.map { it.path },
            )
        }

    @Test
    fun realModeKeepsTheRealHttpClient() {
        val koin = koin(AppEnvironment.Mock, BackendMode.Real)

        assertTrue(koin.getOrNull<StubEngine>() == null)
    }

    @Test
    fun initKoinUsesTheStubDefaultForMock() {
        val koin = initKoin(AppEnvironment.Mock).koin
        try {
            assertIs<StubHomeRepository>(koin.get<HomeRepository>())
        } finally {
            stopKoin()
        }
    }
}
