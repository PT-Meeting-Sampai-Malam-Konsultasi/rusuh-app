package com.rusuh.app.di

import com.rusuh.app.analytics.NoOpAnalytics
import com.rusuh.app.crash.NoOpCrashReporter
import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.data.storage.AppPreferences
import com.rusuh.app.data.storage.FakeAppPreferences
import com.rusuh.app.data.storage.FakeSecureStore
import com.rusuh.app.data.storage.SecureStore
import com.rusuh.app.feature.account.AccountViewModel
import com.rusuh.app.feature.auth.SignInViewModel
import com.rusuh.app.feature.consent.ConsentViewModel
import com.rusuh.app.feature.detail.DetailViewModel
import com.rusuh.app.feature.eticket.CalendarEvent
import com.rusuh.app.feature.eticket.CalendarLaunchResult
import com.rusuh.app.feature.eticket.CalendarLauncher
import com.rusuh.app.feature.eticket.ETicketViewModel
import com.rusuh.app.feature.home.HomeViewModel
import com.rusuh.app.feature.payment.PaymentResultViewModel
import com.rusuh.app.feature.payment.PaymentViewModel
import com.rusuh.app.feature.queues.MyQueuesViewModel
import com.rusuh.app.feature.tickets.TicketsViewModel
import com.rusuh.app.feature.waitingroom.WaitingRoomViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/** Every screen's ViewModel must be buildable from the real Koin graph (a missing binding crashes the screen). */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModuleResolutionTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun koin(mode: BackendMode) =
        koinApplication {
            allowOverride(true)
            modules(
                module {
                    single { AppEnvironment.Staging }
                    single { true }
                    single<SecureStore> { FakeSecureStore() }
                    single<AppPreferences> { FakeAppPreferences() }
                    single<CalendarLauncher> {
                        object : CalendarLauncher {
                            override fun launch(event: CalendarEvent) = CalendarLaunchResult.Success
                        }
                    }
                },
                storageModule,
                coreModule(),
                *dataModules(mode).toTypedArray(),
                analyticsModule(mode) { NoOpAnalytics },
                crashReporterModule(mode) { NoOpCrashReporter },
                appModule,
            )
        }.koin

    @Test
    fun everyViewModelResolvesInLiveMode() {
        val koin = koin(BackendMode.Real)

        assertNotNull(koin.get<HomeViewModel>())
        assertNotNull(koin.get<WaitingRoomViewModel> { parametersOf("evt_1") })
        assertNotNull(koin.get<SignInViewModel>())
        assertNotNull(koin.get<AccountViewModel>())
        assertNotNull(koin.get<ETicketViewModel> { parametersOf("bk_1") })
        assertNotNull(koin.get<DetailViewModel> { parametersOf("evt_1") })
        assertNotNull(koin.get<MyQueuesViewModel>())
        assertNotNull(koin.get<TicketsViewModel>())
        assertNotNull(koin.get<PaymentViewModel> { parametersOf("bk_1") })
        assertNotNull(koin.get<PaymentResultViewModel> { parametersOf("bk_1") })
        assertNotNull(koin.get<ConsentViewModel>())
    }

    @Test
    fun everyViewModelResolvesInStubMode() {
        val koin = koin(BackendMode.Stub)

        assertNotNull(koin.get<PaymentViewModel> { parametersOf("bk_1") })
        assertNotNull(koin.get<DetailViewModel> { parametersOf("evt_1") })
        assertNotNull(koin.get<WaitingRoomViewModel> { parametersOf("evt_1") })
    }
}
