package com.rusuh.app.di

import com.rusuh.app.data.network.session.SessionManager
import com.rusuh.app.data.network.session.SessionManagerObserver
import com.rusuh.app.data.network.session.SessionStateProvider
import com.rusuh.app.data.platform.AttestationProvider
import com.rusuh.app.data.platform.DevAttestationProvider
import com.rusuh.app.data.platform.platformName
import com.rusuh.app.data.remote.AuthApiService
import com.rusuh.app.data.remote.BookingApiService
import com.rusuh.app.data.remote.DeviceApiService
import com.rusuh.app.data.remote.EventApiService
import com.rusuh.app.data.remote.QueueApiService
import com.rusuh.app.data.repository.RealAuthRepository
import com.rusuh.app.data.repository.RealDetailRepository
import com.rusuh.app.data.repository.RealDeviceRepository
import com.rusuh.app.data.repository.RealHomeRepository
import com.rusuh.app.data.repository.RealMyQueuesRepository
import com.rusuh.app.data.repository.RealQueueRepository
import com.rusuh.app.data.repository.StubAuthRepository
import com.rusuh.app.data.repository.StubDetailRepository
import com.rusuh.app.data.repository.StubMyQueuesRepository
import com.rusuh.app.data.repository.StubQueueRepository
import com.rusuh.app.data.storage.InMemoryTokenStore
import com.rusuh.app.data.stub.StubClock
import com.rusuh.app.data.stub.StubEngine
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.repository.AuthRepository
import com.rusuh.app.domain.repository.DetailRepository
import com.rusuh.app.domain.repository.DeviceRepository
import com.rusuh.app.domain.repository.MyQueuesRepository
import com.rusuh.app.domain.repository.QueueRepository
import com.rusuh.app.domain.session.SessionObserver
import com.rusuh.app.domain.usecase.AuthGeneration
import com.rusuh.app.feature.home.HomeRepository
import com.rusuh.app.feature.home.StubHomeRepository
import com.rusuh.app.feature.tickets.StubTicketsRepository
import com.rusuh.app.feature.tickets.TicketsRepository
import com.rusuh.app.feature.waitingroom.data.repository.StubWaitingRoomRepository
import com.rusuh.app.feature.waitingroom.domain.repository.WaitingRoomRepository
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * shortcut: TEMPORARY. While a repository has no real (network) implementation, [BackendMode.Real] resolves its
 * stub and logs a WARNING instead of crashing, so Staging and Release builds start. Home, the Waiting Room, My
 * Queues, the device identity, auth, and Detail are real now; what is left is Tickets (TICKET-00-M4). Remove this
 * constant, the fallback and its tests when those land. Set it to false (or pass `allowStubFallback = false`) for
 * the strict behaviour: [RealRepositoryNotAvailableException].
 */
const val ALLOW_STUB_FALLBACK_UNTIL_REAL_REPOSITORIES = true

/**
 * The repositories that still resolve to FAKE data in [BackendMode.Real] while the fallback is on, release builds
 * included (each logs one WARNING when first resolved). A release screen backed by one of them shows stub data: this
 * list is the signal, kept in sync with the `notYetReal(...)` bindings by `DataModuleTest` and quoted in
 * docs/data-layer.md. Shrink it as the real repositories land; remove the fallback when it is empty.
 */
val REPOSITORIES_STILL_ON_STUBS: List<String> = listOf("TicketsRepository")

/**
 * Binds every repository interface to its stub or its real implementation, per [mode]; every environment defaults to
 * [BackendMode.Real] (see [BackendMode.defaultFor]), [BackendMode.Stub] is a debug-only opt-in.
 *
 * - [BackendMode.Stub]: stub repositories, and the shared [HttpClient] (from `coreModule`) is replaced by one on an
 *   in-process [StubEngine], so ApiServices built on the injected `ApiClient`s run with zero network. The
 *   [ServerClock] is the demo's ticking [StubClock], so countdowns run. The client is closed with Koin.
 * - [BackendMode.Real]: the [ServerClock] is the one from `coreModule`, synced from `serverNow` in responses.
 *   Home, Waiting Room, My Queues, Queue, Device, Auth, and Detail are the network implementations. A repository
 *   without one yet (Tickets) resolves to its stub with [allowStubFallback] and logs a WARNING through [warn] once
 *   (singletons); without it, resolving throws [RealRepositoryNotAvailableException]. When a feature ticket builds
 *   a real repository, bind it in place of the `notYetReal(...)` line of that repository below.
 *
 * The ApiServices and the `DeviceRepository` are bound in both modes: in stub mode the ApiServices run on the
 * [StubEngine]. The [SessionManager] is bound per mode (Real: the secure `TokenStore`; Stub: an in-memory one).
 * `AuthRepository` is the network one in Real and a hand-written demo
 * (`StubAuthRepository`, code 123456) in Stub; both drive the same [SessionManager].
 *
 * Load it after `coreModule()`. A function, not a `val`, for the same reason as `coreModule()`.
 */
fun dataModule(
    mode: BackendMode,
    allowStubFallback: Boolean = ALLOW_STUB_FALLBACK_UNTIL_REAL_REPOSITORIES,
    warn: (String) -> Unit = { println(it) },
): Module =
    module {
        single<AttestationProvider> { DevAttestationProvider() }

        fun <T> notYetReal(
            repository: String,
            stub: () -> T,
        ): T = stubOrFail(repository, allowStubFallback, warn, stub)

        single { EventApiService(get(ApiClientQualifier.Api)) }
        // Device registration and queue endpoints share the queue path prefix on the staging host.
        single { DeviceApiService(get(ApiClientQualifier.Queue)) }
        single { QueueApiService(get(ApiClientQualifier.Queue)) }
        // Auth is served by the core (api) host, like the refresh call of the session layer.
        single { AuthApiService(get(ApiClientQualifier.Api)) }
        // Booking API (categories, attendee validation, booking create/get/cancel).
        single { BookingApiService(get(ApiClientQualifier.Api), get()) }
        // Auth state layer (both modes): the sign-out counter and the read-only session view the screens use.
        single { AuthGeneration() }
        single<SessionObserver> { SessionManagerObserver(get(), get()) }
        single<DeviceRepository> {
            RealDeviceRepository(
                api = get(),
                tokens = get(),
                secureStore = get(),
                cache = get(),
                serverClock = get(),
                platform = platformName,
                attestationProvider = get(),
            )
        }

        when (mode) {
            BackendMode.Stub -> {
                // In-memory tokens: the placeholder tokens of the stub never reach the real secure store, and a stub
                // run always starts signed out.
                single { SessionManager(tokenStore = InMemoryTokenStore(), events = get()) } onClose { it?.close() }
                single<SessionStateProvider> { get<SessionManager>() }
                single { StubClock.ticking() }
                single<ServerClock> { get<StubClock>() }
                single { StubEngine(get()) }
                single<HttpClient> { StubEngine.httpClient(get()) } onClose { it?.close() }
                // shortcut: hand-written stub repositories until the feature tickets build the real ones.
                single<HomeRepository> { StubHomeRepository() }
                single<WaitingRoomRepository> { StubWaitingRoomRepository() }
                single<QueueRepository> { StubQueueRepository(get()) }
                single<TicketsRepository> { StubTicketsRepository() }
                single<DetailRepository> { StubDetailRepository(get()) }
                single<MyQueuesRepository> { StubMyQueuesRepository() }
                single<AuthRepository> { StubAuthRepository(session = get(), clock = get()) }
            }
            BackendMode.Real -> {
                single { SessionManager(tokenStore = get(), events = get()) } onClose { it?.close() }
                single<SessionStateProvider> { get<SessionManager>() }
                // ServerClock: coreModule's MonotonicServerClock, the instance the response plugin keeps in sync.
                single<HomeRepository> { RealHomeRepository(get(), get(), get()) }
                single<QueueRepository> { RealQueueRepository(get(), get(), get(), get(), get(), get(), get()) }
                single<WaitingRoomRepository> { get<QueueRepository>() }
                single<MyQueuesRepository> { RealMyQueuesRepository(get(), get(), get(), get(), get()) }
                single<AuthRepository> {
                    RealAuthRepository(
                        api = get(),
                        devices = get(),
                        session = get(),
                        refresher = get(),
                    )
                }
                single<TicketsRepository> { notYetReal("TicketsRepository") { StubTicketsRepository() } }
                single<DetailRepository> { RealDetailRepository(get(), get()) }
            }
        }
    }

private fun <T> stubOrFail(
    repository: String,
    allowStubFallback: Boolean,
    warn: (String) -> Unit,
    stub: () -> T,
): T {
    if (!allowStubFallback) throw RealRepositoryNotAvailableException(repository)
    warn(
        "WARNING: $repository has no real implementation yet; BackendMode.Real is using its STUB (fake data). " +
            "Temporary, see ALLOW_STUB_FALLBACK_UNTIL_REAL_REPOSITORIES.",
    )
    return stub()
}
