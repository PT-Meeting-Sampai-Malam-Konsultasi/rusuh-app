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
import com.rusuh.app.data.remote.PaymentApiService
import com.rusuh.app.data.remote.QueueApiService
import com.rusuh.app.data.remote.TicketApiService
import com.rusuh.app.data.repository.AuthRepositoryImpl
import com.rusuh.app.data.repository.DetailRepositoryImpl
import com.rusuh.app.data.repository.DeviceRepositoryImpl
import com.rusuh.app.data.repository.HomeRepositoryImpl
import com.rusuh.app.data.repository.MyQueuesRepositoryImpl
import com.rusuh.app.data.repository.QueueRepositoryImpl
import com.rusuh.app.data.repository.StubAuthRepository
import com.rusuh.app.data.repository.StubDetailRepository
import com.rusuh.app.data.repository.StubMyQueuesRepository
import com.rusuh.app.data.repository.StubPaymentRepository
import com.rusuh.app.data.repository.StubQueueRepository
import com.rusuh.app.data.repository.TicketsRepositoryImpl
import com.rusuh.app.data.storage.InMemoryTokenStore
import com.rusuh.app.data.stub.StubClock
import com.rusuh.app.data.stub.StubEngine
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.repository.AuthRepository
import com.rusuh.app.domain.repository.DetailRepository
import com.rusuh.app.domain.repository.DeviceRepository
import com.rusuh.app.domain.repository.MyQueuesRepository
import com.rusuh.app.domain.repository.PaymentRepository
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
 * While a repository has no network implementation, [BackendMode.Real] resolves its stub and logs a warning instead of
 * crashing. Only PaymentRepository is left: the payment routes return 404 on the live backend. Remove the fallback
 * once they are deployed.
 */
const val ALLOW_STUB_FALLBACK_UNTIL_REAL_REPOSITORIES = true

/** Repositories that still resolve to fake data in [BackendMode.Real]; kept in sync with [liveDataModule] by tests. */
val REPOSITORIES_STILL_ON_STUBS: List<String> = listOf("PaymentRepository")

/** [commonDataModule] plus [stubDataModule] or [liveDataModule] for [mode]. Load after `coreModule()`. */
fun dataModules(
    mode: BackendMode,
    allowStubFallback: Boolean = ALLOW_STUB_FALLBACK_UNTIL_REAL_REPOSITORIES,
    warn: (String) -> Unit = { println(it) },
): List<Module> =
    listOf(
        commonDataModule(),
        when (mode) {
            BackendMode.Stub -> stubDataModule()
            BackendMode.Real -> liveDataModule(allowStubFallback, warn)
        },
    )

/** Bindings shared by both modes: ApiServices, device identity and the auth state layer. */
fun commonDataModule(): Module =
    module {
        single<AttestationProvider> { DevAttestationProvider() }

        single { EventApiService(get(ApiClientQualifier.Api)) }
        single { DeviceApiService(get(ApiClientQualifier.Queue)) }
        single { QueueApiService(get(ApiClientQualifier.Queue)) }
        single { AuthApiService(get(ApiClientQualifier.Api)) }
        single { TicketApiService(get(ApiClientQualifier.Api)) }
        single { BookingApiService(get(ApiClientQualifier.Api), get()) }
        single { PaymentApiService(get(ApiClientQualifier.Api)) }
        single { AuthGeneration() }
        single<SessionObserver> { SessionManagerObserver(get(), get()) }
        single<DeviceRepository> {
            DeviceRepositoryImpl(
                api = get(),
                tokens = get(),
                secureStore = get(),
                cache = get(),
                serverClock = get(),
                platform = platformName,
                attestationProvider = get(),
            )
        }
    }

/** Offline mode: stub repositories on an in-process [StubEngine], in-memory tokens and a ticking [StubClock]. */
fun stubDataModule(): Module =
    module {
        single { SessionManager(tokenStore = InMemoryTokenStore(), events = get()) } onClose { it?.close() }
        single<SessionStateProvider> { get<SessionManager>() }
        single { StubClock.ticking() }
        single<ServerClock> { get<StubClock>() }
        single { StubEngine(get()) }
        single<HttpClient> { StubEngine.httpClient(get()) } onClose { it?.close() }
        single<HomeRepository> { StubHomeRepository() }
        single<WaitingRoomRepository> { StubWaitingRoomRepository() }
        single<QueueRepository> { StubQueueRepository(get()) }
        single<TicketsRepository> { StubTicketsRepository() }
        single<DetailRepository> { StubDetailRepository(get()) }
        single<MyQueuesRepository> { StubMyQueuesRepository() }
        single<AuthRepository> { StubAuthRepository(session = get(), clock = get()) }
        single<PaymentRepository> { StubPaymentRepository(get()) }
    }

/** Network mode: repositories over HTTP, secure token store, server clock from `coreModule`. */
fun liveDataModule(
    allowStubFallback: Boolean = ALLOW_STUB_FALLBACK_UNTIL_REAL_REPOSITORIES,
    warn: (String) -> Unit = { println(it) },
): Module =
    module {
        single { SessionManager(tokenStore = get(), events = get()) } onClose { it?.close() }
        single<SessionStateProvider> { get<SessionManager>() }
        single<HomeRepository> { HomeRepositoryImpl(get(), get(), get()) }
        single<QueueRepository> { QueueRepositoryImpl(get(), get(), get(), get(), get(), get(), get()) }
        single<WaitingRoomRepository> { get<QueueRepository>() }
        single<MyQueuesRepository> { MyQueuesRepositoryImpl(get(), get(), get(), get(), get(), get(), get()) }
        single<AuthRepository> {
            AuthRepositoryImpl(
                api = get(),
                devices = get(),
                session = get(),
                refresher = get(),
            )
        }
        single<TicketsRepository> { TicketsRepositoryImpl(get(), get(), get(), get()) }
        single<DetailRepository> { DetailRepositoryImpl(get(), get()) }
        single<PaymentRepository> {
            stubOrFail("PaymentRepository", allowStubFallback, warn) {
                StubPaymentRepository(StubClock.ticking())
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
