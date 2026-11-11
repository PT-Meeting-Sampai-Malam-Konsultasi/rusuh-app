package com.rusuh.app.di

import com.rusuh.app.data.cache.DeviceCache
import com.rusuh.app.data.cache.HomeCache
import com.rusuh.app.data.cache.QueueCache
import com.rusuh.app.data.storage.SecureTokenStore
import com.rusuh.app.data.storage.TokenStore
import org.koin.dsl.module

/** Storage wiring. SecureStore and AppPreferences come from `platformModule`. */
val storageModule =
    module {
        single<TokenStore> { SecureTokenStore(get()) }
        single { HomeCache(get()) }
        single { QueueCache(get()) }
        single { DeviceCache(get()) }
    }
