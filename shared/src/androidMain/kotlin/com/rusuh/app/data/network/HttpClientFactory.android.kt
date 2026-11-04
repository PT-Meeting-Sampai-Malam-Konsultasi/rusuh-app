package com.rusuh.app.data.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

actual object HttpClientFactory {
    actual fun create(config: HttpClientConfig<*>.() -> Unit): HttpClient =
        HttpClient(OkHttp) { config(this) }
}
