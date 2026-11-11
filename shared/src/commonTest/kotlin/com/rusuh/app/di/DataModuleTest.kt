package com.rusuh.app.di

import com.rusuh.app.data.clock.MonotonicServerClock
import com.rusuh.app.data.clock.ServerTimeSink
import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.data.network.buildHttpClient
import com.rusuh.app.data.storage.FakeSecureStore
import com.rusuh.app.data.storage.SecureTokenStore
import com.rusuh.app.data.storage.TokenStore
import com.rusuh.app.data.stub.StubClock
import com.rusuh.app.data.stub.StubEngine
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.repository.DetailRepository
import com.rusuh.app.domain.repository.MyQueuesRepository
import com.rusuh.app.feature.home.HomeRepository
import com.rusuh.app.feature.home.StubHomeRepository
import com.rusuh.app.feature.tickets.TicketsRepository
import com.rusuh.app.feature.waitingroom.domain.repository.WaitingRoomRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.koin.core.Koin
import org.koin.core.KoinApplication
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
    private val warnings = mutableListOf<String>()

    private fun app(
        environment: AppEnvironment,
        mode: BackendMode,
        allowFallback: Boolean = true,
    ): KoinApplication =
        koinApplication {
            allowOverride(true)
            modules(
                module {
                    single { environment }
                    single<TokenStore> { SecureTokenStore(FakeSecureStore()) }
                },
                coreModule(),
                dataModule(mode, allowFallback) { warnings += it },
            )
        }

    private fun koin(
        environment: AppEnvironment,
        mode: BackendMode,
        allowFallback: Boolean = true,
    ): Koin = app(environment, mode, allowFallback).koin

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
    fun strictRealModeFailsLoudlyForEveryRepositoryWithoutANetworkImplementation() {
        val koin = koin(AppEnvironment.Mock, BackendMode.Real, allowFallback = false)

        repositories.forEach { (name, type) ->
            val failure = assertFailsWith<Exception>(name) { koin.get<Any>(type) }
            val messages = generateSequence<Throwable>(failure) { it.cause }.mapNotNull { it.message }.toList()
            assertTrue(
                messages.any { "No real implementation of $name yet" in it && "BackendMode.Stub" in it },
                "$name: $messages",
            )
        }
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun strictRealModeKeepsFailingOnASecondResolution() {
        val koin = koin(AppEnvironment.Mock, BackendMode.Real, allowFallback = false)

        assertFailsWith<Exception> { koin.get<HomeRepository>() }
        assertFailsWith<Exception> { koin.get<HomeRepository>() }
    }

    @Test
    fun realModeFallsBackToTheStubAndWarnsOncePerRepository() {
        val koin = koin(AppEnvironment.Staging, BackendMode.Real)

        repositories.values.forEach { type -> koin.get<Any>(type) }
        repositories.values.forEach { type -> koin.get<Any>(type) } // singletons: no second warning

        assertIs<StubHomeRepository>(koin.get<HomeRepository>())
        assertEquals(repositories.size, warnings.size, "$warnings")
        repositories.keys.forEach { name ->
            assertTrue(warnings.count { it.startsWith("WARNING: $name ") && "STUB" in it } == 1, "$name: $warnings")
        }
    }

    @Test
    fun theFallbackIsOnByDefaultUntilTheRealRepositoriesLand() {
        assertTrue(ALLOW_STUB_FALLBACK_UNTIL_REAL_REPOSITORIES)
    }

    @Test
    fun theStubDemoUsesATickingServerClockSharedWithTheEngine() {
        val koin = koin(AppEnvironment.Mock, BackendMode.Stub)

        assertSame(koin.get<StubClock>(), koin.get<ServerClock>())
        assertSame(koin.get<StubClock>(), koin.get<StubEngine>().clock)
    }

    @Test
    fun realModeReadsTheSameClockInstanceTheResponsePluginUpdates() {
        val koin = koin(AppEnvironment.Staging, BackendMode.Real)

        assertIs<MonotonicServerClock>(koin.get<ServerClock>())
        assertSame<Any>(koin.get<ServerClock>(), koin.get<ServerTimeSink>())
    }

    @Test
    fun stubModeKeepsTheStubClockAndNeverTouchesTheSyncedOne() {
        val koin = koin(AppEnvironment.Mock, BackendMode.Stub)

        assertIs<StubClock>(koin.get<ServerClock>())
        assertTrue(koin.get<ServerClock>() !== koin.get<ServerTimeSink>())
    }

    @Test
    fun aServerNowResponseThroughTheRealClientMovesTheRealModeClock() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        "{\"serverNow\":\"2026-11-14T10:40:00Z\"}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val koin =
                koinApplication {
                    allowOverride(true)
                    modules(
                        module {
                            single<AppEnvironment> { AppEnvironment.Staging }
                            single<TokenStore> { SecureTokenStore(FakeSecureStore()) }
                        },
                        coreModule(),
                        dataModule(BackendMode.Real, true) { },
                        module {
                            single<HttpClient> { buildHttpClient(engine, localeProvider = get(), serverTime = get()) }
                        },
                    )
                }.koin
            val clock = koin.get<MonotonicServerClock>()
            assertEquals(false, clock.isSynced)

            koin.get<ApiClient>(ApiClientQualifier.Api).get<JsonObject>("/bookings/1")

            assertTrue(clock.isSynced)
            assertTrue(koin.get<ServerClock>().nowMillis() >= SERVER_NOW)
        }

    @Test
    fun theStubHttpClientIsClosedWhenKoinStops() {
        val app = app(AppEnvironment.Mock, BackendMode.Stub)
        val client = app.koin.get<HttpClient>()
        assertTrue(client.coroutineContext.job.isActive)

        app.close()

        assertTrue(!client.coroutineContext.job.isActive)
    }

    @Test
    fun stubModeServesTheNamedApiClientsFromTheStubEngine() =
        runTest {
            val koin = koin(AppEnvironment.Mock, BackendMode.Stub)

            val events = koin.get<ApiClient>(ApiClientQualifier.Api).get<JsonObject>("/events")
            val queue = koin.get<ApiClient>(ApiClientQualifier.Queue).get<JsonObject>("/queue/evt_2026_gbk/frontier")

            assertIs<ApiResult.Success<JsonObject>>(events)
            assertIs<ApiResult.Success<JsonObject>>(queue)
        }

    @Test
    fun realModeKeepsTheRealHttpClient() {
        val koin = koin(AppEnvironment.Mock, BackendMode.Real)

        assertTrue(koin.getOrNull<StubEngine>() == null)
        assertTrue(koin.getOrNull<StubClock>() == null)
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

    private companion object {
        // 2026-11-14T10:40:00Z
        const val SERVER_NOW = 1_794_652_800_000L
    }
}
