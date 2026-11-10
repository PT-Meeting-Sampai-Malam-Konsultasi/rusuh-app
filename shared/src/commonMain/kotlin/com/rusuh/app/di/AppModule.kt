package com.rusuh.app.di

import com.rusuh.app.feature.home.HomeRepository
import com.rusuh.app.feature.home.HomeViewModel
import com.rusuh.app.feature.home.StubHomeRepository
import com.rusuh.app.feature.tickets.GetMyTicketsUseCase
import com.rusuh.app.feature.tickets.StubTicketsRepository
import com.rusuh.app.feature.tickets.TicketsRepository
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
    }
