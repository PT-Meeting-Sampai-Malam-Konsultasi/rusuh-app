package com.rusuh.app.di

import com.rusuh.app.data.network.ApiClient
import org.koin.core.qualifier.named

/** Qualifiers of the per-host [ApiClient]s. ApiServices pick the host they talk to with these. */
object ApiClientQualifier {
    val Api = named("api")
    val Queue = named("queue")
    val Pay = named("pay")
}
