package com.rusuh.app.di

import com.rusuh.app.data.clock.MonotonicServerClock
import com.rusuh.app.data.repository.StubDetailRepository
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.repository.DetailRepository
import com.rusuh.app.domain.usecase.CreateBookingUseCase
import com.rusuh.app.domain.usecase.GetDetailUseCase
import com.rusuh.app.domain.usecase.ValidateAttendeeUseCase
import com.rusuh.app.feature.detail.DetailViewModel
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

        // Detail ticket page.
        // shortcut: stub repository + monotonic clock until FR-15/16/17-M1 (#80, #81, #82) land.
        single<ServerClock> { MonotonicServerClock() }
        single<DetailRepository> { StubDetailRepository(get()) }
        factory { GetDetailUseCase(get()) }
        factory { ValidateAttendeeUseCase(get()) }
        factory { CreateBookingUseCase(get()) }
        factory { (eventId: String) -> DetailViewModel(eventId, get(), get(), get(), get()) }
    }
