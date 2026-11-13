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
import com.rusuh.app.data.repository.AuthRepositoryImpl
import com.rusuh.app.data.repository.DetailRepositoryImpl
import com.rusuh.app.data.repository.DeviceRepositoryImpl
import com.rusuh.app.data.repository.HomeRepositoryImpl
import com.rusuh.app.data.repository.MyQueuesRepositoryImpl
import com.rusuh.app.data.repository.PaymentRepositoryImpl
import com.rusuh.app.data.repository.QueueRepositoryImpl
import com.rusuh.app.data.repository.StubAuthRepository
import com.rusuh.app.data.repository.TicketsRepositoryImpl
import com.rusuh.app.data.storage.AppPreferences
import com.rusuh.app.data.storage.FakeAppPreferences
import com.rusuh.app.data.storage.FakeSecureStore
import com.rusuh.app.data.storage.SecureStore
import com.rusuh.app.data.storage.SecureTokenStore
import com.rusuh.app.data.storage.TokenStore
import com.rusuh.app.data.stub.StubClock
import com.rusuh.app.data.stub.StubEngine
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.OtpChallenge
import com.rusuh.app.domain.repository.AuthRepository
import com.rusuh.app.domain.repository.DetailRepository
import com.rusuh.app.domain.repository.DeviceRepository
import com.rusuh.app.domain.repository.MyQueuesRepository
import com.rusuh.app.domain.repository.PaymentRepository
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
    private fun app(
        environment: AppEnvironment,
        mode: BackendMode,
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
                *dataModules(mode).toTypedArray(),
            )
        }

    private fun koin(
        environment: AppEnvironment,
        mode: BackendMode,
    ): Koin = app(environment, mode).koin

    /** Repositories with a network implementation in [BackendMode.Real]. */
    private val realRepositories: Map<String, KClass<*>> =
        mapOf(
            "HomeRepository" to HomeRepository::class,
            "WaitingRoomRepository" to WaitingRoomRepository::class,
            "MyQueuesRepository" to MyQueuesRepository::class,
            "QueueRepository" to QueueRepository::class,
            "DeviceRepository" to DeviceRepository::class,
            "AuthRepository" to AuthRepository::class,
            "TicketsRepository" to TicketsRepository::class,
            "DetailRepository" to DetailRepository::class,
            "PaymentRepository" to PaymentRepository::class,
        )

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

        realRepositories.values.forEach { type -> koin.get<Any>(type) } // throws when a binding is missing or fails
        assertIs<StubHomeRepository>(koin.get<HomeRepository>())
        assertSame(koin.get<HomeRepository>(), koin.get<HomeRepository>())
    }

    @Test
    fun realModeBindsTheNetworkImplementationsForAllRealRepositories() {
        val koin = koin(AppEnvironment.Mock, BackendMode.Real)

        assertIs<HomeRepositoryImpl>(koin.get<HomeRepository>())
        assertIs<QueueRepositoryImpl>(koin.get<QueueRepository>())
        assertIs<MyQueuesRepositoryImpl>(koin.get<MyQueuesRepository>())
        assertIs<DeviceRepositoryImpl>(koin.get<DeviceRepository>())
        assertIs<AuthRepositoryImpl>(koin.get<AuthRepository>())
        assertIs<TicketsRepositoryImpl>(koin.get<TicketsRepository>())
        assertIs<DetailRepositoryImpl>(koin.get<DetailRepository>())
        assertIs<PaymentRepositoryImpl>(koin.get<PaymentRepository>())
        assertSame<Any>(koin.get<QueueRepository>(), koin.get<WaitingRoomRepository>(), "one queue behind both seams")
    }

    @Test
    fun stubModeBindsTheStubAuthRepositoryAndRealModeNeverFallsBackToIt() {
        assertIs<StubAuthRepository>(koin(AppEnvironment.Mock, BackendMode.Stub).get<AuthRepository>())

        val real = koin(AppEnvironment.Staging, BackendMode.Real)
        assertIs<AuthRepositoryImpl>(real.get<AuthRepository>())
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
            assertIs<ApiResult.Success<*>>(stub.get<AuthApiService>().getMe())
        }

    @Test
    fun theTicketsTabFollowsTheSession() =
        runTest {
            val koin =
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
                        *dataModules(BackendMode.Stub).toTypedArray(),
                        appModule,
                    )
                }.koin

            assertFalse(koin.get<SessionManager>().isSignedIn.value)
            assertEquals(MyTickets.SignedOut, koin.get<GetMyTicketsUseCase>()())

            koin.get<SessionManager>().startSession("access-1", "refresh-1")
            assertIs<MyTickets.Content>(koin.get<GetMyTicketsUseCase>()())
        }

    @Test
    fun stubModeKeepsItsPlaceholderTokensOutOfTheRealSecureStore() =
        runTest {
            val secure = FakeSecureStore()
            val koin =
                koinApplication {
                    allowOverride(true)
                    modules(
                        module {
                            single { AppEnvironment.Mock }
                            single<SecureStore> { secure }
                            single<AppPreferences> { FakeAppPreferences() }
                        },
                        storageModule,
                        coreModule(),
                        *dataModules(BackendMode.Stub).toTypedArray(),
                    )
                }.koin
            val auth = koin.get<AuthRepository>()
            val challenge = assertIs<DomainResult.Success<OtpChallenge>>(auth.requestOtp("stub.user@example.test")).data

            auth.verifyOtp(challenge.challengeId, "123456")

            assertTrue(koin.get<SessionManager>().isSignedIn.value)
            assertTrue(secure.entries.isEmpty(), "${secure.entries.keys}")
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
                        *dataModules(BackendMode.Real).toTypedArray(),
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

            val events = koin.get<ApiClient>(ApiClientQualifier.Api).get<JsonObject>("/core/events")
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
