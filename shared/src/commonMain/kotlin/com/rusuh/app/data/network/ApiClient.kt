package com.rusuh.app.data.network

import com.rusuh.app.data.clock.ServerTimeSink
import com.rusuh.app.data.network.plugin.AcceptLanguagePlugin
import com.rusuh.app.data.network.plugin.AuthHeadersPlugin
import com.rusuh.app.data.network.plugin.ServerClockPlugin
import com.rusuh.app.data.network.plugin.SessionRefreshPlugin
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
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json

/**
 * Shared HTTP entry point for every ApiService. Each helper returns an [ApiResult]
 * that keeps the HTTP status; success bodies are the payload itself (no envelope).
 * Use `ApiResult<Unit>` for endpoints that answer 204 No Content.
 */
@Suppress("TooManyFunctions") // one helper per HTTP verb plus the raw-bytes helper
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
                ApiResult.Error(
                    code = response.status.value,
                    problem = readProblem(response),
                    retryAfterSeconds = readRetryAfter(response),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ApiResult.Failure(failure)
        }

    /**
     * Raw bytes of a successful response, for downloads such as a ticket PDF. No JSON `Accept` is forced
     * ([accept] defaults to any type). Server-sent event streams are not supported here: use [client] directly.
     */
    suspend fun getBytes(
        path: String,
        accept: ContentType = ContentType.Any,
        block: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<ByteArray> =
        execute(
            method = HttpMethod.Get,
            path = path,
            block = {
                accept(accept)
                block()
            },
            readBody = { response -> response.body<ByteArray>() },
        )

    /** Seconds form of `Retry-After` only; the HTTP-date form is ignored (null) as the API sends seconds. */
    private fun readRetryAfter(response: HttpResponse): Int? =
        response.headers[HttpHeaders.RetryAfter]?.trim()?.toIntOrNull()?.takeIf { it >= 0 }

    /**
     * Reads an `application/problem+json` body; null when absent, of another type, malformed,
     * or larger than [MAX_PROBLEM_BYTES] (a problem body is tiny, so anything bigger is not one).
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun readProblem(response: HttpResponse): ProblemDetails? {
        val isProblem = response.contentType()?.match(ContentType.Application.ProblemJson) == true
        val declaredLength = response.contentLength()
        return if (isProblem && (declaredLength == null || declaredLength <= MAX_PROBLEM_BYTES)) {
            try {
                val bytes = response.bodyAsChannel().readRemaining(MAX_PROBLEM_BYTES + 1L).readByteArray()
                if (bytes.size > MAX_PROBLEM_BYTES) {
                    null
                } else {
                    defaultJson.decodeFromString(ProblemDetails.serializer(), bytes.decodeToString())
                }
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
        internal fun HttpClientConfig<*>.configure(
            baseUrl: String,
            enableLogging: Boolean = false,
            logger: (String) -> Unit = {},
            requestTimeoutMs: Long = REQUEST_TIMEOUT_MS,
        ) {
            configurePlugins(enableLogging, logger, requestTimeoutMs)
            useBaseUrl(baseUrl)
        }

        /**
         * Shared plugins only, with no host: one such client (one engine) serves every [forHost] client. The
         * language header and server clock are on every request of every host; they are skipped when their
         * provider is null (plain tests). Per-host token plugins come from [forHost].
         */
        @Suppress("LongParameterList") // independent knobs of the one shared client configuration
        internal fun HttpClientConfig<*>.configurePlugins(
            enableLogging: Boolean = false,
            logger: (String) -> Unit = {},
            requestTimeoutMs: Long = REQUEST_TIMEOUT_MS,
            localeProvider: LocaleProvider? = null,
            serverTime: ServerTimeSink? = null,
            logLevel: LogLevel = LogLevel.INFO,
        ) {
            followRedirects = false // a redirect must never carry token headers to another host
            install(ContentNegotiation) { json(defaultJson) }
            install(HttpTimeout) {
                requestTimeoutMillis = requestTimeoutMs
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = SOCKET_TIMEOUT_MS
            }
            localeProvider?.let { provider -> install(AcceptLanguagePlugin) { this.localeProvider = provider } }
            serverTime?.let { target -> install(ServerClockPlugin) { sink = target } }
            if (enableLogging) {
                install(Logging) {
                    sanitizeHeader { name -> SENSITIVE_HEADERS.any { it.equals(name, ignoreCase = true) } }
                    this.logger =
                        object : Logger {
                            override fun log(message: String) = logger(message)
                        }
                    level = logLevel
                }
            }
            // No default Accept: ContentNegotiation adds it for JSON calls, raw calls choose their own.
        }

        internal fun HttpClientConfig<*>.useBaseUrl(baseUrl: String) {
            defaultRequest { url(baseUrl.trimEnd('/') + "/") }
        }

        /**
         * Token headers and 401 handling for one host, see [HostAuth]. Installed once per host client because
         * the shared [HttpClient] serves hosts that may differ.
         */
        internal fun HttpClientConfig<*>.installHostAuth(hostAuth: HostAuth) {
            install(AuthHeadersPlugin) {
                tokenStore = hostAuth.tokenStore
                sendDeviceToken = hostAuth.sendDeviceToken
                sendBearer = hostAuth.sendBearer
                onSignedIn = hostAuth.refreshCoordinator?.let { it::notifySignedIn }
            }
            hostAuth.refreshCoordinator?.let { sessions ->
                install(SessionRefreshPlugin) { coordinator = sessions }
            }
        }

        /**
         * An [ApiClient] for [baseUrl] that reuses [shared] (its engine and plugins) through `HttpClient.config`.
         * The derived client shares the engine but does not own it: closing one does not affect the others.
         * The owner of [shared] closes it (coreModule does, on Koin close). [hostAuth] adds this host's token
         * headers and 401 refresh; null sends no tokens.
         */
        fun forHost(
            shared: HttpClient,
            baseUrl: String,
            hostAuth: HostAuth? = null,
        ): ApiClient =
            ApiClient(
                baseUrl = baseUrl,
                client =
                    shared.config {
                        useBaseUrl(baseUrl)
                        hostAuth?.let { installHostAuth(it) }
                    },
            )

        private val SENSITIVE_HEADERS =
            setOf(
                HttpHeaders.Authorization,
                "X-Device-Token",
                "X-Queue-Token",
                "X-Admission-Token",
                "X-Turnstile-Token",
                HttpHeaders.Cookie,
                HttpHeaders.SetCookie,
            )

        /** Upper bound for an error body that is still parsed as problem details. */
        internal const val MAX_PROBLEM_BYTES = 64 * 1024
    }
}

/** Sets [body] as a JSON request body. */
inline fun <reified B> HttpRequestBuilder.jsonBody(body: B) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

/**
 * Marks a mutating request as safe to retry: the server de-duplicates repeats carrying the same [key]. Take the key
 * from `newIdempotencyKey()`, once per user attempt, and reuse it on every retry of that attempt; never retry a
 * charging POST with a different key.
 */
fun HttpRequestBuilder.idempotencyKey(key: String) {
    require(key.isNotBlank()) { "Idempotency-Key must not be blank" }
    header(IDEMPOTENCY_KEY_HEADER, key)
}

private const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
