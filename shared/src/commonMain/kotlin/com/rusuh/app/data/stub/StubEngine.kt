package com.rusuh.app.data.stub

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.buildHostClient
import com.rusuh.app.data.network.buildHttpClient
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
    /** The percent-decoded `{param}` values of the stub route that served it. */
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
 * - Routing: among the scripted and the default routes the one with the most fixed path segments answers (a
 *   script wins a tie), so scripting `/bookings/{bookingId}` leaves `/bookings/active` served. Query strings
 *   are ignored: a filter never changes the answer.
 * - A request no route serves gets a problem response with `code: STUB_NO_ROUTE` and the header
 *   `X-Stub-Miss: true` (404, or 405 when the path exists under another method), so a test can tell "not
 *   stubbed" from a real NOT_FOUND.
 * - [script] replaces a route's responses for a scenario.
 * - Requests are recorded only when [recordRequests] is true (tests opt in; the demo does not keep bodies), and
 *   only the last [maxRecordedRequests].
 * - Safe for parallel requests: routing, script progress and the log are guarded by one lock.
 */
class StubEngine(
    val clock: StubClock = StubClock(),
    private val routes: List<StubRoute> = StubResponses.routes,
    private val recordRequests: Boolean = false,
    private val maxRecordedRequests: Int = DEFAULT_MAX_RECORDED,
) {
    private val lock = StubLock()
    private val scripts = mutableListOf<Script>()
    private val log = ArrayDeque<RecordedRequest>()

    /** The recorded requests (empty unless [recordRequests]), oldest first. */
    val requests: List<RecordedRequest> get() = lock.withLock { log.toList() }

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
        lock.withLock {
            scripts.removeAll { it.route.method == method && it.route.template == template }
            scripts += Script(StubRoute(method, template, responses.first()), responses)
        }
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
        lock.withLock {
            scripts.clear()
            log.clear()
        }
    }

    private suspend fun MockRequestHandleScope.serve(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath
        val body = if (recordRequests) request.bodyText() else null
        val response =
            lock.withLock {
                val scripted = StubResponses.find(request.method, path, scripts.map { it.route })
                val default = StubResponses.find(request.method, path, routes)
                val useScript = scripted != null && (default == null || scripted.specificity >= default.specificity)
                val match = if (useScript) scripted else default
                if (recordRequests) record(request, path, body, match)
                when {
                    useScript -> scripts.first { it.route === scripted?.route }.next()
                    match != null -> match.route.response
                    else -> miss(request.method, path)
                }
            }
        return respond(
            content = clock.render(response.body),
            status = HttpStatusCode.fromValue(response.status),
            headers = responseHeaders(response),
        )
    }

    private val StubMatch.specificity get() = route.literalSegments

    private fun record(
        request: HttpRequestData,
        path: String,
        body: String?,
        match: StubMatch?,
    ) {
        log +=
            RecordedRequest(
                method = request.method,
                path = path,
                query = request.url.encodedQuery,
                headers = request.headers.entries().associate { it.key to it.value },
                body = body,
                pathParams = match?.pathParams.orEmpty(),
            )
        while (log.size > maxRecordedRequests) log.removeFirst()
    }

    private fun responseHeaders(response: StubResponse) =
        HeadersBuilder().apply {
            response.contentType?.let { append(HttpHeaders.ContentType, it) }
            response.headers.forEach { (name, value) -> append(name, value) }
        }.build()

    private fun miss(
        method: HttpMethod,
        path: String,
    ): StubResponse {
        val status = if (StubResponses.pathExists(path, routes)) 405 else 404
        return StubResponse.problem(
            status = status,
            body =
                """{"type":"about:blank","title":"No stub","status":$status,"code":"$MISS_CODE",""" +
                    """"detail":"No stub registered for ${method.value} $path"}""",
            headers = mapOf(MISS_HEADER to "true"),
        )
    }

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

        /** `code` of the problem body of a request no stub route serves. */
        const val MISS_CODE = "STUB_NO_ROUTE"

        /** Response header set (to `true`) on a request no stub route serves. */
        const val MISS_HEADER = "X-Stub-Miss"

        const val DEFAULT_MAX_RECORDED = 200

        /** The app's configured [HttpClient] on [stub]'s engine, to feed [apiClient]. */
        fun httpClient(stub: StubEngine): HttpClient = buildHttpClient(stub.engine)
    }
}

/** An [ApiClient] for [baseUrl] served by this stub. Several such clients may share [shared]. */
fun StubEngine.apiClient(
    baseUrl: String = StubEngine.DEFAULT_BASE_URL,
    shared: HttpClient = StubEngine.httpClient(this),
): ApiClient = buildHostClient(shared, baseUrl)
