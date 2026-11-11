package com.rusuh.app.di

import com.rusuh.app.data.clock.MonotonicServerClock
import com.rusuh.app.data.repository.StubDetailRepository
import com.rusuh.app.data.repository.StubMyQueuesRepository
import com.rusuh.app.data.stub.StubClock
import com.rusuh.app.data.stub.StubEngine
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.repository.DetailRepository
import com.rusuh.app.domain.repository.MyQueuesRepository
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
 * stub and logs a WARNING instead of crashing, so Staging and Release builds start. Remove this constant, the
 * fallback and its tests when the real repositories land: Home (EPIC-04 M4 API integration), Waiting Room (FR-04-M4
 * #30), Tickets (TICKET-00-M4), Detail (FR-15/16/17-M1 #80 #81 #82) and My Queues (FR-APP-01-M4 #50). Set it to
 * false (or pass `allowStubFallback = false`) for the strict behaviour: [RealRepositoryNotAvailableException].
 */
const val ALLOW_STUB_FALLBACK_UNTIL_REAL_REPOSITORIES = true

/**
 * Binds every repository interface to its stub or its real implementation, per [mode]; Mock runs [BackendMode.Stub]
 * by default (see [BackendMode.defaultFor]).
 *
 * - [BackendMode.Stub]: stub repositories, and the shared [HttpClient] (from `coreModule`) is replaced by one on an
 *   in-process [StubEngine], so ApiServices built on the injected `ApiClient`s run with zero network. The
 *   [ServerClock] is the demo's ticking [StubClock], so countdowns run. The client is closed with Koin.
 * - [BackendMode.Real]: no real repository exists yet. With [allowStubFallback] each one resolves to its stub and
 *   logs a WARNING through [warn] once (singletons); without it, resolving throws
 *   [RealRepositoryNotAvailableException]. When a feature ticket builds a real repository, bind it in place of the
 *   `notYetReal(...)` line of that repository below.
 *
 * Load it after `coreModule()`. A function, not a `val`, for the same reason as `coreModule()`.
 */
fun dataModule(
    mode: BackendMode,
    allowStubFallback: Boolean = ALLOW_STUB_FALLBACK_UNTIL_REAL_REPOSITORIES,
    warn: (String) -> Unit = { println(it) },
): Module =
    module {
        fun <T> notYetReal(
            repository: String,
            stub: () -> T,
        ): T = stubOrFail(repository, allowStubFallback, warn, stub)

        when (mode) {
            BackendMode.Stub -> {
                single { StubClock.ticking() }
                single<ServerClock> { get<StubClock>() }
                single { StubEngine(get()) }
                single<HttpClient> { StubEngine.httpClient(get()) } onClose { it?.close() }
                // shortcut: hand-written stub repositories until the feature tickets build the real ones.
                single<HomeRepository> { StubHomeRepository() }
                single<WaitingRoomRepository> { StubWaitingRoomRepository() }
                single<TicketsRepository> { StubTicketsRepository() }
                single<DetailRepository> { StubDetailRepository(get()) }
                single<MyQueuesRepository> { StubMyQueuesRepository() }
            }
            BackendMode.Real -> {
                // shortcut: monotonic clock until FR-15/16/17-M1 (#80, #81, #82) sync it with the server.
                single<ServerClock> { MonotonicServerClock() }
                single<HomeRepository> { notYetReal("HomeRepository") { StubHomeRepository() } }
                single<WaitingRoomRepository> { notYetReal("WaitingRoomRepository") { StubWaitingRoomRepository() } }
                single<TicketsRepository> { notYetReal("TicketsRepository") { StubTicketsRepository() } }
                single<DetailRepository> { notYetReal("DetailRepository") { StubDetailRepository(get()) } }
                single<MyQueuesRepository> { notYetReal("MyQueuesRepository") { StubMyQueuesRepository() } }
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
