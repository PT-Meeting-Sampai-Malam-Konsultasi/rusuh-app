package com.rusuh.app.data.network

import com.rusuh.app.data.clock.ServerTimeSink
import com.rusuh.app.data.network.ApiClient.Companion.configurePlugins
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.logging.LogLevel

/**
 * The ONE place that turns an engine into the app's configured [HttpClient] and a host client from it. The real
 * client (`coreModule`, platform engine) and the in-process `StubEngine` both build through here, so a change to
 * how plugins or per-host defaults are installed touches only this file.
 *
 * @param engine null picks the platform engine; tests and the stub backend pass a `MockEngine`.
 * @param localeProvider source of `Accept-Language` on every request.
 * @param serverTime receives `serverNow` from responses; null (the stub backend) leaves the clock alone.
 * @param logLevel level of the opt-in logging plugin (masked token headers).
 */
internal fun buildHttpClient(
    engine: HttpClientEngine? = null,
    localeProvider: LocaleProvider = SystemLocaleProvider(),
    serverTime: ServerTimeSink? = null,
    logLevel: LogLevel = LogLevel.INFO,
): HttpClient {
    val configure: HttpClientConfig<*>.() -> Unit = {
        configurePlugins(localeProvider = localeProvider, serverTime = serverTime, logLevel = logLevel)
    }
    return if (engine == null) HttpClientFactory.create(configure) else HttpClient(engine, configure)
}

/** An [ApiClient] for [baseUrl] on [shared]'s engine and plugins; [hostAuth] adds the host's tokens and 401 refresh. */
internal fun buildHostClient(
    shared: HttpClient,
    baseUrl: String,
    hostAuth: HostAuth? = null,
): ApiClient = ApiClient.forHost(shared, baseUrl, hostAuth)
