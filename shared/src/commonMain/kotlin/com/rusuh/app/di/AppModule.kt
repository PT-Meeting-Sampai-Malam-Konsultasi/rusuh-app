package com.rusuh.app.di

import com.rusuh.app.data.clock.MonotonicServerClock
import com.rusuh.app.data.repository.StubDetailRepository
import com.rusuh.app.data.repository.StubMyQueuesRepository
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.repository.DetailRepository
import com.rusuh.app.domain.repository.MyQueuesRepository
import com.rusuh.app.domain.usecase.CreateBookingUseCase
import com.rusuh.app.domain.usecase.GetDetailUseCase
import com.rusuh.app.domain.usecase.GetMyQueuesUseCase
import com.rusuh.app.domain.usecase.ValidateAttendeeUseCase
import com.rusuh.app.feature.detail.DetailViewModel
import com.rusuh.app.feature.home.HomeRepository
import com.rusuh.app.feature.home.HomeViewModel
import com.rusuh.app.feature.home.StubHomeRepository
import com.rusuh.app.feature.queues.MyQueuesViewModel
import com.rusuh.app.feature.tickets.GetMyTicketsUseCase
import com.rusuh.app.feature.tickets.StubTicketsRepository
import com.rusuh.app.feature.tickets.TicketsRepository
import com.rusuh.app.feature.tickets.TicketsViewModel
import com.rusuh.app.feature.waitingroom.WaitingRoomViewModel
import com.rusuh.app.feature.waitingroom.data.repository.StubWaitingRoomRepository
import com.rusuh.app.feature.waitingroom.domain.repository.WaitingRoomRepository
import com.rusuh.app.feature.waitingroom.domain.usecase.ObserveQueuePlaceUseCase
import org.koin.dsl.module

/** Main app module. Register shared (common) dependencies here. */
val appModule =
    module {
        // Home feature.
        // shortcut: StubHomeRepository until M4 (API integration) provides the real one.
        single<HomeRepository> { StubHomeRepository() }
        factory { HomeViewModel(get()) }

        // Waiting Room.
        // shortcut: StubWaitingRoomRepository (scripted) until FR-04-M4 (#30) provides the real queue API.
        single<WaitingRoomRepository> { StubWaitingRoomRepository() }
        factory { ObserveQueuePlaceUseCase(get()) }
        factory { (eventId: String) -> WaitingRoomViewModel(eventId, get()) }

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

        // My Queues (Antrian tab).
        // shortcut: StubMyQueuesRepository until FR-APP-01-M4 (#50) provides the real one.
        single<MyQueuesRepository> { StubMyQueuesRepository() }
        factory { GetMyQueuesUseCase(get()) }
        factory { MyQueuesViewModel(get()) }
        factory { TicketsViewModel(get()) }
    }
