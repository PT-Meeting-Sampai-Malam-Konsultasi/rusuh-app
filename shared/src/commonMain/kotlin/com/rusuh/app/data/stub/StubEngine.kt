package com.rusuh.app.data.stub

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiClient.Companion.configurePlugins
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent

/** One request the [StubEngine] received, for assertions. [path] is the encoded path, without host or query. */
data class RecordedRequest(
    val method: HttpMethod,
    val path: String,
    val query: String,
    val headers: Map<String, List<String>>,
    val body: String?,
    /** The `{param}` values of the stub route that served it. */
    val pathParams: Map<String, String>,
) {
    /** First value of header [name], case-insensitive. */
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
}

/**
 * In-process backend: a Ktor `MockEngine` that serves [StubResponses] (clock-rendered) with zero network, so the
 * real [ApiClient] and repositories run against spec payloads. Shared by unit tests and the demo mode.
 *
 * Unscripted requests get the default response of the matching route; a request no route matches gets a 404
 * problem response (and is still recorded). [script] replaces a route's responses for a scenario. Not
 * thread-safe: meant for single-threaded tests and a demo session.
 */
class StubEngine(
    val clock: StubClock = StubClock(),
    private val routes: List<StubRoute> = StubResponses.routes,
) {
    private val scripts = mutableListOf<Script>()
    private val log = mutableListOf<RecordedRequest>()

    /** Every request received so far, oldest first. */
    val requests: List<RecordedRequest> get() = log.toList()

    val engine: MockEngine = MockEngine { request -> serve(request) }

    /**
     * Serves [responses] in order for [method] and [template] (the spec path, e.g. `/queue/{eventId}/frontier`);
     * the last one repeats forever. Scripting the same route again replaces the previous script.
     */
    fun script(
        method: HttpMethod,
        template: String,
        responses: List<StubResponse>,
    ) {
        require(responses.isNotEmpty()) { "A script needs at least one response" }
        scripts.removeAll { it.route.method == method && it.route.template == template }
        scripts += Script(StubRoute(method, template, responses.first()), responses)
    }

    fun script(
        method: HttpMethod,
        template: String,
        response: StubResponse,
    ) = script(method, template, listOf(response))

    fun script(
        method: HttpMethod,
        template: String,
        error: StubError,
    ) = script(method, template, error.response)

    /** Removes every script and the request log; defaults apply again. */
    fun reset() {
        scripts.clear()
        log.clear()
    }

    private suspend fun MockRequestHandleScope.serve(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath
        val scripted = StubResponses.find(request.method, path, scripts.map { it.route })
        val match = scripted ?: StubResponses.find(request.method, path, routes)
        log +=
            RecordedRequest(
                method = request.method,
                path = path,
                query = request.url.encodedQuery,
                headers = request.headers.entries().associate { it.key to it.value },
                body = request.bodyText(),
                pathParams = match?.pathParams.orEmpty(),
            )
        val response =
            when {
                scripted != null -> scripts.first { it.route === scripted.route }.next()
                match != null -> match.route.response
                else -> notFound(request)
            }
        return respond(
            content = clock.render(response.body),
            status = HttpStatusCode.fromValue(response.status),
            headers = responseHeaders(response),
        )
    }

    private fun responseHeaders(response: StubResponse) =
        HeadersBuilder().apply {
            response.contentType?.let { append(HttpHeaders.ContentType, it) }
            response.headers.forEach { (name, value) -> append(name, value) }
        }.build()

    private fun notFound(request: HttpRequestData) =
        StubResponse.problem(
            status = 404,
            body =
                """{"type":"about:blank","title":"No stub","status":404,"code":"NOT_FOUND",""" +
                    """"detail":"No stub registered for ${request.method.value} ${request.url.encodedPath}"}""",
        )

    private fun HttpRequestData.bodyText(): String? =
        when (val content = body) {
            is TextContent -> content.text
            is ByteArrayContent -> content.bytes().decodeToString()
            else -> null
        }

    private class Script(
        val route: StubRoute,
        private val responses: List<StubResponse>,
    ) {
        private var index = 0

        fun next(): StubResponse = responses[index.coerceAtMost(responses.lastIndex)].also { index++ }
    }

    companion object {
        /** Host of stub clients; `.invalid` never resolves, so a stray real call can never succeed. */
        const val DEFAULT_BASE_URL = "https://stub.rusuh.invalid"

        /** An [HttpClient] with the shared app plugins on [stub]'s engine, to feed `ApiClient.forHost`. */
        fun httpClient(stub: StubEngine): HttpClient = HttpClient(stub.engine) { configurePlugins() }
    }
}

/** An [ApiClient] for [baseUrl] served by this stub. Several such clients may share [httpClient]. */
fun StubEngine.apiClient(
    baseUrl: String = StubEngine.DEFAULT_BASE_URL,
    shared: HttpClient = StubEngine.httpClient(this),
): ApiClient = ApiClient.forHost(shared, baseUrl)
