package com.rusuh.app.data.network

import com.rusuh.app.data.remote.dto.ProblemDetails
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Shared HTTP entry point for every ApiService. Each helper returns an [ApiResult]
 * that keeps the HTTP status; success bodies are the payload itself (no envelope).
 * Use `ApiResult<Unit>` for endpoints that answer 204 No Content.
 */
class ApiClient(
    val baseUrl: String,
    private val enableLogging: Boolean = false,
    private val logger: (String) -> Unit = { println(it) },
    val client: HttpClient = createHttpClient(baseUrl, enableLogging, logger),
) {
    suspend inline fun <reified T> request(
        method: HttpMethod,
        path: String,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = execute(method, path, block) { response -> response.body<T>() }

    suspend inline fun <reified T> get(
        path: String,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = request(HttpMethod.Get, path, block)

    suspend inline fun <reified T> post(
        path: String,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = request(HttpMethod.Post, path, block)

    suspend inline fun <reified T> put(
        path: String,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = request(HttpMethod.Put, path, block)

    suspend inline fun <reified T> patch(
        path: String,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = request(HttpMethod.Patch, path, block)

    suspend inline fun <reified T> delete(
        path: String,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = request(HttpMethod.Delete, path, block)

    /**
     * Runs one call and folds every outcome into an [ApiResult]. [readBody] decodes a 2xx response.
     * Cancellation is always rethrown, never turned into a result.
     */
    @PublishedApi
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun <T> execute(
        method: HttpMethod,
        path: String,
        block: HttpRequestBuilder.() -> Unit,
        readBody: suspend (HttpResponse) -> T,
    ): ApiResult<T> =
        try {
            val response =
                client.request {
                    this.method = method
                    url(path.removePrefix("/"))
                    block()
                }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.status.value, readBody(response))
            } else {
                ApiResult.Error(response.status.value, readProblem(response))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ApiResult.Exception(failure)
        }

    /** Reads an `application/problem+json` body; null when absent, of another type, or malformed. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun readProblem(response: HttpResponse): ProblemDetails? {
        val isProblem = response.contentType()?.match(ContentType.Application.ProblemJson) == true
        return if (isProblem) {
            try {
                defaultJson.decodeFromString(ProblemDetails.serializer(), response.bodyAsText())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (malformed: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun close() = client.close()

    companion object {
        const val REQUEST_TIMEOUT_MS = 30_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val SOCKET_TIMEOUT_MS = 30_000L

        val defaultJson: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            }

        fun createHttpClient(
            baseUrl: String,
            enableLogging: Boolean,
            logger: (String) -> Unit,
        ): HttpClient = HttpClientFactory.create { configure(baseUrl, enableLogging, logger) }

        /** Installs the shared plugins and defaults; also used to build test clients on a mock engine. */
        fun HttpClientConfig<*>.configure(
            baseUrl: String,
            enableLogging: Boolean = false,
            logger: (String) -> Unit = {},
            requestTimeoutMs: Long = REQUEST_TIMEOUT_MS,
        ) {
            install(ContentNegotiation) { json(defaultJson) }
            install(HttpTimeout) {
                requestTimeoutMillis = requestTimeoutMs
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
            defaultRequest {
                url(baseUrl.trimEnd('/') + "/")
                accept(ContentType.Application.Json)
            }
        }
    }
}

/** Sets [body] as a JSON request body. */
inline fun <reified B> HttpRequestBuilder.jsonBody(body: B) {
    contentType(ContentType.Application.Json)
    setBody(body)
}
