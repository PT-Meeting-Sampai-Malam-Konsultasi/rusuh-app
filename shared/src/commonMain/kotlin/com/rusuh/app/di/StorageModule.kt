package com.rusuh.app.di

import com.rusuh.app.data.cache.AuthCache
import com.rusuh.app.data.cache.DeviceCache
import com.rusuh.app.data.cache.HomeCache
import com.rusuh.app.data.cache.QueueCache
import com.rusuh.app.data.cache.TicketsCache
import com.rusuh.app.data.storage.SecureTokenStore
import com.rusuh.app.data.storage.TokenStore
import com.rusuh.app.domain.repository.AccountEmailStore
import com.rusuh.app.domain.repository.DisplayNameStore
import org.koin.dsl.module

/** Storage wiring. SecureStore and AppPreferences come from `platformModule`. */
val storageModule =
    module {
        single<TokenStore> { SecureTokenStore(get()) }
        single { HomeCache(get()) }
        single { QueueCache(get()) }
        single { DeviceCache(get()) }
        single { TicketsCache(get()) }
        single { AuthCache(get()) }
        single<DisplayNameStore> { get<AuthCache>() }
        single<AccountEmailStore> { get<AuthCache>() }
    }
