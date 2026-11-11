package com.rusuh.app.di

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiClient.Companion.configurePlugins
import com.rusuh.app.data.network.ApiConfig
import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.data.network.HttpClientFactory
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Pure infrastructure: the environment-driven [ApiConfig], the one shared [HttpClient] and one [ApiClient]
 * per backend host. Feature modules inject these and never build their own. Needs an [AppEnvironment]
 * binding, which [initKoin] provides. A function rather than a `val`: Koin keeps singleton instances inside
 * the module object, so each Koin start (and each test) needs a fresh one.
 */
fun coreModule(): Module =
    module {
        single { ApiConfig.forEnvironment(get<AppEnvironment>()) }
        single<HttpClient> { HttpClientFactory.create { configurePlugins() } }
        single(ApiClientQualifier.Api) { ApiClient.forHost(get(), get<ApiConfig>().baseUrl) }
        single(ApiClientQualifier.Queue) { ApiClient.forHost(get(), get<ApiConfig>().queueBaseUrl) }
        single(ApiClientQualifier.Pay) { ApiClient.forHost(get(), get<ApiConfig>().payBaseUrl) }
    }
