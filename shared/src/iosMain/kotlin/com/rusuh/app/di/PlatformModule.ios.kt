package com.rusuh.app.di

import com.rusuh.app.data.platform.AttestationProvider
import com.rusuh.app.data.platform.DevAttestationProvider
import com.rusuh.app.data.storage.AppPreferences
import com.rusuh.app.data.storage.KeychainSecureStore
import com.rusuh.app.data.storage.SecureStore
import com.rusuh.app.data.storage.UserDefaultsAppPreferences
import com.rusuh.app.feature.payment.IosPaymentPageLauncher
import com.rusuh.app.feature.payment.PaymentPageLauncher
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module =
    module {
        single<SecureStore> { KeychainSecureStore() }
        single<AppPreferences> { UserDefaultsAppPreferences() }
        single<AttestationProvider> { DevAttestationProvider() }
        single<PaymentPageLauncher> { IosPaymentPageLauncher() }
    }
