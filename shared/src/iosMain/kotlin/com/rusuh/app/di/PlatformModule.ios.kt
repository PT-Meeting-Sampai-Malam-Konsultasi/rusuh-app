package com.rusuh.app.di

import com.rusuh.app.data.platform.AttestationProvider
import com.rusuh.app.data.platform.DevAttestationProvider
import com.rusuh.app.data.storage.AppPreferences
import com.rusuh.app.data.storage.KeychainSecureStore
import com.rusuh.app.data.storage.SecureStore
import com.rusuh.app.data.storage.UserDefaultsAppPreferences
import com.rusuh.app.feature.eticket.CalendarLauncher
import com.rusuh.app.feature.eticket.IosCalendarLauncher
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module =
    module {
        single<SecureStore> { KeychainSecureStore() }
        single<AppPreferences> { UserDefaultsAppPreferences() }
        single<AttestationProvider> { DevAttestationProvider() }
        // iOS calendar: the presenter callback is provided from Swift via setCalendarPresenter().
        // Until then, launch() returns NoCalendarApp. Verified on simulator by user.
        single<CalendarLauncher> { IosCalendarLauncher(iosCalendarPresenter) }
    }

/**
 * Callback to present EKEventEditViewController from Swift. Set this from iOSApp.swift
 * after app launch to enable the calendar feature. Returns true if presentation succeeded.
 */
var iosCalendarPresenter: (platform.EventKit.EKEvent, platform.EventKit.EKEventStore) -> Boolean =
    { _, _ -> false }
