package com.rusuh.app.di

import com.rusuh.app.data.repository.StubMyQueuesRepository
import com.rusuh.app.domain.repository.MyQueuesRepository
import com.rusuh.app.domain.usecase.GetMyQueuesUseCase
import com.rusuh.app.feature.home.HomeRepository
import com.rusuh.app.feature.home.HomeViewModel
import com.rusuh.app.feature.home.StubHomeRepository
import com.rusuh.app.feature.queues.MyQueuesViewModel
import com.rusuh.app.feature.tickets.GetMyTicketsUseCase
import com.rusuh.app.feature.tickets.StubTicketsRepository
import com.rusuh.app.feature.tickets.TicketsRepository
import com.rusuh.app.feature.tickets.TicketsViewModel
import org.koin.dsl.module

/** Main app module. Register shared (common) dependencies here. */
val appModule =
    module {
        // Home feature.
        // shortcut: StubHomeRepository until M4 (API integration) provides the real one.
        single<HomeRepository> { StubHomeRepository() }
        factory { HomeViewModel(get()) }

        // Tickets (Tiket tab).
        // shortcut: StubTicketsRepository parses JSON strings until TICKET-00-M4 provides the real one.
        single<TicketsRepository> { StubTicketsRepository() }
        factory { GetMyTicketsUseCase(get()) }

        // My Queues (Antrian tab).
        // shortcut: StubMyQueuesRepository until FR-APP-01-M4 (#50) provides the real one.
        single<MyQueuesRepository> { StubMyQueuesRepository() }
        factory { GetMyQueuesUseCase(get()) }
        factory { MyQueuesViewModel(get()) }
        factory { TicketsViewModel(get()) }
    }
