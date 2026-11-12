package com.rusuh.app.data.repository

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.HostAuth
import com.rusuh.app.data.network.plugin.testHttp
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.io.IOException

/** One scripted answer of the [FakeBackend]. */
class Reply(
    val status: Int,
    val body: String = "",
    val problem: Boolean = false,
    val retryAfter: Int? = null,
    val failure: Throwable? = null,
) {
    companion object {
        fun ok(body: String) = Reply(200, body)

        fun created(body: String) = Reply(201, body)

        fun noContent() = Reply(204)

        fun problem(
            status: Int,
            code: String,
            retryAfter: Int? = null,
        ) = Reply(status, problemJson(status, code), problem = true, retryAfter = retryAfter)

        /** What the real host answers today for every route: a plain 404, not even problem+json. */
        fun notFound() = Reply(404, """{"error":"not found"}""")

        fun offline() = Reply(0, failure = IOException("offline"))
    }
}

/** A request the backend saw, `"POST /queue/evt_1/join"` style line plus headers. */
class Seen(
    val line: String,
    val headers: Map<String, String>,
    val body: String,
)

/**
 * A scripted backend on a Ktor MockEngine. `on("GET /core/events", reply...)` answers calls to that route in order and
 * repeats the last reply; a route without a script answers like the real host today (404). The recorded requests are
 * in [seen].
 */
class FakeBackend(
    hostAuth: HostAuth? = null,
) {
    val seen = mutableListOf<Seen>()

    /** Runs when a request arrives, before it is answered (to hold requests open in concurrency tests). */
    var onRequest: (suspend (String) -> Unit)? = null
    private val scripts = mutableMapOf<String, MutableList<Reply>>()
    private val counters = mutableMapOf<String, Int>()

    fun on(
        line: String,
        vararg replies: Reply,
    ) {
        scripts[line] = replies.toMutableList()
        counters[line] = 0
    }

    /** How many times [line] was called. */
    fun callsTo(line: String): Int = seen.count { it.line == line }

    val api: ApiClient =
        testHttp(hostAuth = hostAuth) { request ->
            val line = "${request.method.value} ${request.url.encodedPath}"
            seen +=
                Seen(
                    line = line,
                    headers = request.headers.entries().associate { (name, values) -> name to values.joinToString() },
                    body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty(),
                )
            onRequest?.invoke(line)
            val script = scripts[line]
            val reply =
                if (script.isNullOrEmpty()) {
                    Reply.notFound()
                } else {
                    val index = counters.getValue(line)
                    counters[line] = index + 1
                    script[minOf(index, script.lastIndex)]
                }
            reply.failure?.let { throw it }
            val headers =
                buildList {
                    if (reply.body.isNotEmpty()) {
                        add(
                            HttpHeaders.ContentType to
                                if (reply.problem) "application/problem+json" else "application/json",
                        )
                    }
                    reply.retryAfter?.let { add(HttpHeaders.RetryAfter to it.toString()) }
                }
            respond(
                content = reply.body,
                status = HttpStatusCode.fromValue(reply.status),
                headers = headersOf(*headers.map { (name, value) -> name to listOf(value) }.toTypedArray()),
            )
        }.api
}
