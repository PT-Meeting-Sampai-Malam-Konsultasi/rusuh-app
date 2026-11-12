package com.rusuh.app.di

import com.rusuh.app.data.network.LocaleProvider
import com.rusuh.app.data.network.session.SessionManager
import com.rusuh.app.domain.model.UserLocale
import com.rusuh.app.domain.usecase.CreateBookingUseCase
import com.rusuh.app.domain.usecase.GetDetailUseCase
import com.rusuh.app.domain.usecase.GetMyQueuesUseCase
import com.rusuh.app.domain.usecase.GetProfileUseCase
import com.rusuh.app.domain.usecase.LeaveQueueUseCase
import com.rusuh.app.domain.usecase.ObserveSessionUseCase
import com.rusuh.app.domain.usecase.RequestOtpUseCase
import com.rusuh.app.domain.usecase.SaveDisplayNameUseCase
import com.rusuh.app.domain.usecase.SignOutUseCase
import com.rusuh.app.domain.usecase.ValidateAttendeeUseCase
import com.rusuh.app.domain.usecase.VerifyOtpUseCase
import com.rusuh.app.feature.account.AccountViewModel
import com.rusuh.app.feature.auth.SignInViewModel
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
        // Signed out shows the "Masuk" state; awaitSignedIn waits (bounded) for the cold-start session restore.
        factory { GetMyTicketsUseCase(get(), isSignedIn = get<SessionManager>()::awaitSignedIn) }

        // Auth and Account (AUTH-00 #77, ACCOUNT-00 #104).
        // The AuthGeneration and the SessionObserver singletons are bound in dataModule(): singletons of this
        // top-level `val` module would survive across Koin starts (tests).
        factory {
            val language = get<LocaleProvider>()
            RequestOtpUseCase(get()) { UserLocale.fromWire(language.language().tag) }
        }
        factory { VerifyOtpUseCase(get(), get()) }
        factory { SignOutUseCase(get(), get(), get()) }
        factory { GetProfileUseCase(get(), get()) }
        factory { SaveDisplayNameUseCase(get()) }
        factory { ObserveSessionUseCase(get()) }
        factory { SignInViewModel(get(), get(), get(), get(), get()) }
        factory { AccountViewModel(get(), get(), get()) }

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
