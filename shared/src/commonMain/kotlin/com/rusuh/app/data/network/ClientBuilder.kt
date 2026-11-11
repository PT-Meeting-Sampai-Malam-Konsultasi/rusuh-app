package com.rusuh.app.data.network

import com.rusuh.app.data.network.ApiClient.Companion.configurePlugins
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine

/**
 * The ONE place that turns an engine into the app's configured [HttpClient] and a host client from it. The real
 * client (`coreModule`, platform engine) and the in-process `StubEngine` both build through here, so a change to
 * how plugins or per-host defaults are installed touches only this file.
 *
 * @param engine null picks the platform engine; tests and the stub backend pass a `MockEngine`.
 */
internal fun buildHttpClient(engine: HttpClientEngine? = null): HttpClient =
    if (engine == null) {
        HttpClientFactory.create { configurePlugins() }
    } else {
        HttpClient(engine) { configurePlugins() }
    }

/** An [ApiClient] for [baseUrl] on [shared]'s engine and plugins. */
internal fun buildHostClient(
    shared: HttpClient,
    baseUrl: String,
): ApiClient = ApiClient.forHost(shared, baseUrl)
