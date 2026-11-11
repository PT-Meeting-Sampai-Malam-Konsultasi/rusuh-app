package com.rusuh.app.di

import com.rusuh.app.data.repository.StubDetailRepository
import com.rusuh.app.data.repository.StubMyQueuesRepository
import com.rusuh.app.data.stub.StubClock
import com.rusuh.app.data.stub.StubEngine
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

/**
 * Binds every repository interface to its stub or its real implementation, per [mode]. Today only stubs exist:
 * in [BackendMode.Real] a repository without a network implementation throws
 * [RealRepositoryNotAvailableException] when first resolved, never silently falling back to a stub. When a feature
 * ticket builds the real repository, replace the `unavailable(...)` line of that repository below.
 *
 * In [BackendMode.Stub] it also replaces the shared [HttpClient] (from `coreModule`) with one served by an
 * in-process [StubEngine], so ApiServices built on the injected `ApiClient`s run with zero network. Load it after
 * `coreModule()`. A function, not a `val`, for the same reason as `coreModule()`.
 */
fun dataModule(mode: BackendMode): Module =
    module {
        when (mode) {
            BackendMode.Stub -> {
                single { StubClock() }
                single { StubEngine(get()) }
                single<HttpClient> { StubEngine.httpClient(get()) }
                // shortcut: hand-written stub repositories until the feature tickets build the real ones.
                single<HomeRepository> { StubHomeRepository() }
                single<WaitingRoomRepository> { StubWaitingRoomRepository() }
                single<TicketsRepository> { StubTicketsRepository() }
                single<DetailRepository> { StubDetailRepository(get()) }
                single<MyQueuesRepository> { StubMyQueuesRepository() }
            }
            BackendMode.Real -> {
                single<HomeRepository> { unavailable("HomeRepository") }
                single<WaitingRoomRepository> { unavailable("WaitingRoomRepository") }
                single<TicketsRepository> { unavailable("TicketsRepository") }
                single<DetailRepository> { unavailable("DetailRepository") }
                single<MyQueuesRepository> { unavailable("MyQueuesRepository") }
            }
        }
    }

private fun unavailable(repository: String): Nothing = throw RealRepositoryNotAvailableException(repository)
