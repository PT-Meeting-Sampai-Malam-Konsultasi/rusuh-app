package com.rusuh.app.di

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiConfig
import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.data.network.buildHostClient
import com.rusuh.app.data.network.buildHttpClient
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * Pure infrastructure: the environment-driven [ApiConfig], the one shared [HttpClient] and one [ApiClient]
 * per backend host. Feature modules inject these and never build their own. Needs an [AppEnvironment]
 * binding, which [initKoin] provides. A function rather than a `val`: Koin keeps singleton instances inside
 * the module object, so each Koin start (and each test) needs a fresh one.
 */
fun coreModule(): Module =
    module {
        single {
            val environment = get<AppEnvironment>()
            ApiConfig.forEnvironment(environment).also { config ->
                if (environment != AppEnvironment.Mock && config.usesPlaceholderHosts) {
                    // Visible, not silent: requests to `.invalid` hosts can never succeed. No tokens or secrets here.
                    println("WARNING: $environment build uses placeholder API hosts: $config")
                }
            }
        }
        single<HttpClient> { buildHttpClient() } onClose { it?.close() }
        single(ApiClientQualifier.Api) { buildHostClient(get(), get<ApiConfig>().baseUrl) }
        single(ApiClientQualifier.Queue) { buildHostClient(get(), get<ApiConfig>().queueBaseUrl) }
        single(ApiClientQualifier.Pay) { buildHostClient(get(), get<ApiConfig>().payBaseUrl) }
    }
