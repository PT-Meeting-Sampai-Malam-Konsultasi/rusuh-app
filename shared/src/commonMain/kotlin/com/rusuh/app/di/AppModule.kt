package com.rusuh.app.di

import com.rusuh.app.feature.home.HomeRepository
import com.rusuh.app.feature.home.HomeViewModel
import com.rusuh.app.feature.home.StubHomeRepository
import com.rusuh.app.feature.tickets.GetMyTicketsUseCase
import com.rusuh.app.feature.tickets.StubTicketsRepository
import com.rusuh.app.feature.tickets.TicketsRepository
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
    }
