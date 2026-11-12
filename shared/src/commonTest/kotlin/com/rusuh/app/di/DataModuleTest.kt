package com.rusuh.app.di

import com.rusuh.app.data.clock.MonotonicServerClock
import com.rusuh.app.data.clock.ServerTimeSink
import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.data.network.buildHttpClient
import com.rusuh.app.data.network.session.SessionManager
import com.rusuh.app.data.remote.AuthApiService
import com.rusuh.app.data.remote.dto.RequestOtpDto
import com.rusuh.app.data.repository.RealAuthRepository
import com.rusuh.app.data.repository.RealDeviceRepository
import com.rusuh.app.data.repository.RealHomeRepository
import com.rusuh.app.data.repository.RealMyQueuesRepository
import com.rusuh.app.data.repository.RealQueueRepository
import com.rusuh.app.data.repository.StubAuthRepository
import com.rusuh.app.data.storage.AppPreferences
import com.rusuh.app.data.storage.FakeAppPreferences
import com.rusuh.app.data.storage.FakeSecureStore
import com.rusuh.app.data.storage.SecureStore
import com.rusuh.app.data.storage.SecureTokenStore
import com.rusuh.app.data.storage.TokenStore
import com.rusuh.app.data.stub.StubClock
import com.rusuh.app.data.stub.StubEngine
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.repository.AuthRepository
import com.rusuh.app.domain.repository.DetailRepository
import com.rusuh.app.domain.repository.DeviceRepository
import com.rusuh.app.domain.repository.MyQueuesRepository
import com.rusuh.app.domain.repository.QueueRepository
import com.rusuh.app.feature.home.HomeRepository
import com.rusuh.app.feature.home.StubHomeRepository
import com.rusuh.app.feature.tickets.GetMyTicketsUseCase
import com.rusuh.app.feature.tickets.MyTickets
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
import kotlin.test.assertFalse
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
                    single<SecureStore> { FakeSecureStore() }
                    single<AppPreferences> { FakeAppPreferences() }
                },
                storageModule,
                coreModule(),
                dataModule(mode, allowFallback) { warnings += it },
            )
        }

    private fun koin(
        environment: AppEnvironment,
        mode: BackendMode,
        allowFallback: Boolean = true,
    ): Koin = app(environment, mode, allowFallback).koin

    /** Repositories with a network implementation in [BackendMode.Real]. */
    private val realRepositories: Map<String, KClass<*>> =
        mapOf(
            "HomeRepository" to HomeRepository::class,
            "WaitingRoomRepository" to WaitingRoomRepository::class,
            "MyQueuesRepository" to MyQueuesRepository::class,
            "QueueRepository" to QueueRepository::class,
            "DeviceRepository" to DeviceRepository::class,
            "AuthRepository" to AuthRepository::class,
        )

    /** Repositories that still fall back to their stub in [BackendMode.Real]. */
    private val stubOnlyRepositories: Map<String, KClass<*>> =
        mapOf(
            "TicketsRepository" to TicketsRepository::class,
            "DetailRepository" to DetailRepository::class,
        )

    private val repositories: Map<String, KClass<*>> = realRepositories + stubOnlyRepositories

    @Test
    fun everyEnvironmentDefaultsToRealHttp() {
        AppEnvironment.entries.forEach { assertEquals(BackendMode.Real, BackendMode.defaultFor(it), "$it") }
    }

    @Test
    fun resolveAppliesTheOverrideAndTheDefault() {
        assertEquals(BackendMode.Real, BackendMode.resolve(AppEnvironment.Mock))
        assertEquals(BackendMode.Real, BackendMode.resolve(AppEnvironment.Mock, ""))
        assertEquals(BackendMode.Real, BackendMode.resolve(AppEnvironment.Mock, null))
        assertEquals(BackendMode.Stub, BackendMode.resolve(AppEnvironment.Mock, "stub"))
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
    fun realModeBindsTheNetworkImplementationsForHomeQueueMyQueuesAndDevice() {
        val koin = koin(AppEnvironment.Mock, BackendMode.Real, allowFallback = false)

        assertIs<RealHomeRepository>(koin.get<HomeRepository>())
        assertIs<RealQueueRepository>(koin.get<QueueRepository>())
        assertIs<RealMyQueuesRepository>(koin.get<MyQueuesRepository>())
        assertIs<RealDeviceRepository>(koin.get<DeviceRepository>())
        assertIs<RealAuthRepository>(koin.get<AuthRepository>())
        assertSame<Any>(koin.get<QueueRepository>(), koin.get<WaitingRoomRepository>(), "one queue behind both seams")
        assertTrue(warnings.isEmpty(), "no stub is used for these: $warnings")
    }

    @Test
    fun stubModeBindsTheStubAuthRepositoryAndRealModeNeverFallsBackToIt() {
        assertIs<StubAuthRepository>(koin(AppEnvironment.Mock, BackendMode.Stub).get<AuthRepository>())

        val real = koin(AppEnvironment.Staging, BackendMode.Real)
        assertIs<RealAuthRepository>(real.get<AuthRepository>())
        assertTrue(warnings.none { "AuthRepository" in it }, "$warnings")
        assertTrue("AuthRepository" !in REPOSITORIES_STILL_ON_STUBS)
    }

    @Test
    fun oneSessionManagerIsSharedByTheAuthRepositoryAndTheApp() {
        listOf(BackendMode.Stub, BackendMode.Real).forEach { mode ->
            val koin = koin(AppEnvironment.Mock, mode)

            assertSame(koin.get<SessionManager>(), koin.get<SessionManager>())
            assertFalse(koin.get<SessionManager>().isSignedIn.value, "$mode: nothing stored yet")
        }
    }

    @Test
    fun theAuthApiServiceRunsOnTheCoreHostInBothModes() =
        runTest {
            val stub = koin(AppEnvironment.Mock, BackendMode.Stub)
            val challenge = stub.get<AuthApiService>().requestOtp(RequestOtpDto(email = "test.user@example.test"))
            assertIs<ApiResult.Success<*>>(challenge)
            assertIs<ApiResult.Success<*>>(stub.get<AuthApiService>().getProfile())
        }

    @Test
    fun theTicketsTabFollowsTheSessionManager() =
        runTest {
            val app =
                koinApplication {
                    allowOverride(true)
                    modules(
                        module {
                            single { AppEnvironment.Mock }
                            single<SecureStore> { FakeSecureStore() }
                            single<AppPreferences> { FakeAppPreferences() }
                        },
                        storageModule,
                        coreModule(),
                        dataModule(BackendMode.Stub, true) { },
                        appModule,
                    )
                }
            val koin = app.koin
            val tickets = koin.get<GetMyTicketsUseCase>()
            assertEquals(MyTickets.SignedOut, tickets())

            koin.get<SessionManager>().startSession("access-1", "refresh-1")

            assertIs<MyTickets.Content>(tickets())
            koin.get<AuthRepository>().logout()
            assertEquals(MyTickets.SignedOut, tickets())
            app.close()
        }

    @Test
    fun strictRealModeFailsLoudlyOnlyForTheRepositoriesWithoutANetworkImplementation() {
        val koin = koin(AppEnvironment.Mock, BackendMode.Real, allowFallback = false)

        stubOnlyRepositories.forEach { (name, type) ->
            val failure = assertFailsWith<Exception>(name) { koin.get<Any>(type) }
            val messages = generateSequence<Throwable>(failure) { it.cause }.mapNotNull { it.message }.toList()
            assertTrue(
                messages.any { "No real implementation of $name yet" in it && "BackendMode.Stub" in it },
                "$name: $messages",
            )
        }
        realRepositories.values.forEach { type -> koin.get<Any>(type) } // resolves without the strict failure
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun theDocumentedListOfStubRepositoriesMatchesTheFallbackBindings() {
        assertEquals(stubOnlyRepositories.keys, REPOSITORIES_STILL_ON_STUBS.toSet())
    }

    @Test
    fun strictRealModeKeepsFailingOnASecondResolution() {
        val koin = koin(AppEnvironment.Mock, BackendMode.Real, allowFallback = false)

        assertFailsWith<Exception> { koin.get<DetailRepository>() }
        assertFailsWith<Exception> { koin.get<DetailRepository>() }
    }

    @Test
    fun realModeFallsBackToTheStubOnlyForDetailAndTicketsAndWarnsOncePerRepository() {
        val koin = koin(AppEnvironment.Staging, BackendMode.Real)

        repositories.values.forEach { type -> koin.get<Any>(type) }
        repositories.values.forEach { type -> koin.get<Any>(type) } // singletons: no second warning

        assertEquals(stubOnlyRepositories.size, warnings.size, "$warnings")
        stubOnlyRepositories.keys.forEach { name ->
            assertTrue(warnings.count { it.startsWith("WARNING: $name ") && "STUB" in it } == 1, "$name: $warnings")
        }
        assertTrue(warnings.none { realName -> realRepositories.keys.any { it in realName } }, "$warnings")
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
    fun initKoinStartsTheStubWhenAskedForIt() {
        val koin = initKoin(AppEnvironment.Mock, BackendMode.Stub).koin
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
