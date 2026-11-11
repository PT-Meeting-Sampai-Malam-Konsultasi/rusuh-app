package com.rusuh.app.di

import com.rusuh.app.data.clock.MonotonicServerClock
import com.rusuh.app.data.clock.ServerTimeSink
import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiClient.Companion.configurePlugins
import com.rusuh.app.data.network.ApiConfig
import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.data.network.HostAuth
import com.rusuh.app.data.network.HttpClientFactory
import com.rusuh.app.data.network.LocaleProvider
import com.rusuh.app.data.network.SystemLocaleProvider
import com.rusuh.app.data.network.session.ApiTokenRefresher
import com.rusuh.app.data.network.session.SessionEvents
import com.rusuh.app.data.network.session.SessionRefreshCoordinator
import com.rusuh.app.data.network.session.TokenRefresher
import com.rusuh.app.domain.clock.ServerClock
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.binds
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * Pure infrastructure: the environment-driven [ApiConfig], the one shared [HttpClient] and one [ApiClient]
 * per backend host. Feature modules inject these and never build their own. Needs an [AppEnvironment]
 * binding, which [initKoin] provides, and a `TokenStore` (storageModule). A function rather than a `val`: Koin
 * keeps singleton instances inside the module object, so each Koin start (and each test) needs a fresh one.
 *
 * Plugins: the shared client sends `Accept-Language` and records `serverNow` into the one [ServerClock]; each host
 * client adds the token headers and the single-flight 401 refresh (see [HostAuth]).
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
        single<LocaleProvider> { SystemLocaleProvider() }
        single { MonotonicServerClock() } binds arrayOf(ServerClock::class, ServerTimeSink::class)
        single { SessionEvents() }
        single {
            SessionRefreshCoordinator(
                tokenStore = get(),
                // Lazy: the default refresher needs the Api client, which needs this coordinator.
                refresher = lazy { get<TokenRefresher>() },
                events = get(),
            )
        }
        single<TokenRefresher> { ApiTokenRefresher(get(ApiClientQualifier.Api)) }
        single<HttpClient> {
            HttpClientFactory.create { configurePlugins(localeProvider = get(), serverTime = get()) }
        } onClose { it?.close() }
        single(ApiClientQualifier.Api) { hostClient { baseUrl } }
        single(ApiClientQualifier.Queue) { hostClient { queueBaseUrl } }
        single(ApiClientQualifier.Pay) { hostClient { payBaseUrl } }
    }

private fun Scope.hostClient(baseUrl: ApiConfig.() -> String): ApiClient =
    ApiClient.forHost(
        shared = get(),
        baseUrl = get<ApiConfig>().baseUrl(),
        hostAuth = HostAuth(tokenStore = get(), refreshCoordinator = get()),
    )
