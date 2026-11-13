package com.rusuh.app.di

import com.rusuh.app.data.platform.AttestationProvider
import com.rusuh.app.data.platform.DevAttestationProvider
import com.rusuh.app.data.storage.AndroidSecureStore
import com.rusuh.app.data.storage.AppPreferences
import com.rusuh.app.data.storage.SecureStore
import com.rusuh.app.data.storage.SharedPrefsAppPreferences
import com.rusuh.app.feature.eticket.AndroidCalendarLauncher
import com.rusuh.app.feature.eticket.CalendarLauncher
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module =
    module {
        single<SecureStore> { AndroidSecureStore(androidContext()) }
        single<AppPreferences> { SharedPrefsAppPreferences(androidContext()) }
        single<AttestationProvider> { DevAttestationProvider() }
        single<CalendarLauncher> { AndroidCalendarLauncher { androidContext() } }
    }
