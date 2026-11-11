package com.rusuh.app.di

import com.rusuh.app.data.network.AppEnvironment

/** Called from Swift (iOSApp.init) as `KoinIosKt.doInitKoin(environmentName:)`; see [AppEnvironment.fromName]. */
fun doInitKoin(environmentName: String) {
    initKoin(AppEnvironment.fromName(environmentName))
}

/**
 * Same as [doInitKoin] with an explicit backend mode override (`"Stub"` or `"Real"`; null or blank keeps the
 * default for the environment, see [BackendMode.resolve]). Swift:
 * `KoinIosKt.doInitKoinWithBackend(environmentName: "Mock", backendModeName: "Real")`.
 */
fun doInitKoinWithBackend(
    environmentName: String,
    backendModeName: String?,
) {
    val environment = AppEnvironment.fromName(environmentName)
    initKoin(environment, BackendMode.resolve(environment, backendModeName))
}
