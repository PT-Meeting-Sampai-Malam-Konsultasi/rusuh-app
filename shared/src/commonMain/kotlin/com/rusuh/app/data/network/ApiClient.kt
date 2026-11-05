package com.rusuh.app.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ApiClient(
    val baseUrl: String,
    private val enableLogging: Boolean = false,
    private val logger: (String) -> Unit = { println(it) },
    val client: HttpClient = createHttpClient(baseUrl, enableLogging, logger),
) {
    suspend inline fun <reified T> request(
        method: HttpMethod,
        path: String,
        crossinline block: HttpRequestBuilder.() -> Unit = {},
    ): NetworkResult<T> =
        safeCall {
            val response =
                client.request {
                    this.method = method
                    url(path.removePrefix("/"))
                    block()
                }
            handle(response)
        }

    suspend inline fun <reified T> get(
        path: String,
        crossinline block: HttpRequestBuilder.() -> Unit = {},
    ): NetworkResult<T> = request(HttpMethod.Get, path, block)

    suspend inline fun <reified T> post(
        path: String,
        crossinline block: HttpRequestBuilder.() -> Unit = {},
    ): NetworkResult<T> = request(HttpMethod.Post, path, block)

    suspend inline fun <reified T> handle(response: HttpResponse): NetworkResult<T> =
        if (response.status.isSuccess()) {
            NetworkResult.Success(response.body<T>())
        } else {
            NetworkResult.Error(
                message = response.status.description,
                code = response.status.value,
            )
        }

    suspend inline fun <T> safeCall(block: () -> NetworkResult<T>): NetworkResult<T> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            NetworkResult.Error(message = "Request timed out", cause = e)
        } catch (e: SerializationException) {
            NetworkResult.Error(message = "Failed to parse response: ${e.message}", cause = e)
        } catch (e: IOException) {
            NetworkResult.Error(message = e.message ?: "Network I/O error", cause = e)
        }

    fun close() = client.close()

    companion object {
        const val REQUEST_TIMEOUT_MS = 30_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val SOCKET_TIMEOUT_MS = 30_000L

        val defaultJson: Json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
                explicitNulls = false
            }

        fun createHttpClient(
            baseUrl: String,
            enableLogging: Boolean,
            logger: (String) -> Unit,
        ): HttpClient =
            HttpClientFactory.create {
                install(ContentNegotiation) { json(defaultJson) }
                install(HttpTimeout) {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS
                    socketTimeoutMillis = SOCKET_TIMEOUT_MS
                }
                if (enableLogging) {
                    install(Logging) {
                        this.logger =
                            object : Logger {
                                override fun log(message: String) = logger(message)
                            }
                        level = LogLevel.INFO
                    }
                }
                defaultRequest { url(baseUrl.trimEnd('/') + "/") }
            }
    }
}
