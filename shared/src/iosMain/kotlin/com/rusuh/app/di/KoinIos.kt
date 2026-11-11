package com.rusuh.app.di

import com.rusuh.app.data.network.AppEnvironment

/** Called from Swift (iOSApp.init) as `KoinIosKt.doInitKoin(environmentName:)`; see [AppEnvironment.fromName]. */
fun doInitKoin(environmentName: String) {
    initKoin(AppEnvironment.fromName(environmentName))
}
