package com.rusuh.app.di

import com.rusuh.app.domain.usecase.CreateBookingUseCase
import com.rusuh.app.domain.usecase.GetDetailUseCase
import com.rusuh.app.domain.usecase.GetMyQueuesUseCase
import com.rusuh.app.domain.usecase.LeaveQueueUseCase
import com.rusuh.app.domain.usecase.ValidateAttendeeUseCase
import com.rusuh.app.feature.detail.DetailViewModel
import com.rusuh.app.feature.home.HomeViewModel
import com.rusuh.app.feature.queues.MyQueuesViewModel
import com.rusuh.app.feature.tickets.GetMyTicketsUseCase
import com.rusuh.app.feature.tickets.TicketsViewModel
import com.rusuh.app.feature.waitingroom.WaitingRoomViewModel
import com.rusuh.app.feature.waitingroom.domain.usecase.ObserveQueuePlaceUseCase
import org.koin.dsl.module

/**
 * Main app module: use cases and view models. Repositories and the ServerClock are bound per mode in [dataModule].
 */
val appModule =
    module {
        // Home feature.
        factory { HomeViewModel(get()) }

        // Waiting Room.
        factory { ObserveQueuePlaceUseCase(get()) }
        factory { (eventId: String) -> WaitingRoomViewModel(eventId, get(), get()) }
        // TODO(FR-04-M4 #30): the Waiting Room's LeaveQueue intent calls this once the ViewModel handles it.
        factory { LeaveQueueUseCase(get()) }

        // Tickets (Tiket tab).
        factory { GetMyTicketsUseCase(get()) }

        // Detail ticket page (the ServerClock is bound per backend mode in dataModule).
        factory { GetDetailUseCase(get()) }
        factory { ValidateAttendeeUseCase(get()) }
        factory { CreateBookingUseCase(get()) }
        factory { (eventId: String) -> DetailViewModel(eventId, get(), get(), get(), get()) }

        // My Queues (Antrian tab).
        factory { GetMyQueuesUseCase(get()) }
        factory { MyQueuesViewModel(get()) }
        factory { TicketsViewModel(get()) }
    }
