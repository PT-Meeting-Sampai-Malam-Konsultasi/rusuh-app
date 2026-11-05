package com.rusuh.app.data.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

expect object HttpClientFactory {
    fun create(config: HttpClientConfig<*>.() -> Unit = {}): HttpClient
}
