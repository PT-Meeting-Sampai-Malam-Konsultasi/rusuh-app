package com.rusuh.app.data.network.sse

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.remote.dto.ProblemDetails
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val MAX_PROBLEM_BYTES = 64 * 1024L

/** Result of opening an SSE stream. */
sealed interface SseConnection {
    data class Stream(val events: Flow<SseEvent>) : SseConnection

    /** Non-success HTTP response with optional problem details. */
    data class HttpError(
        val status: Int,
        val problem: ProblemDetails?,
        val retryAfterSeconds: Int?,
    ) : SseConnection

    /** Network or I/O failure. */
    data class Failure(val cause: Throwable) : SseConnection
}

/**
 * Opens an SSE stream and emits parsed events. Uses `prepareGet` + `bodyAsChannel` to work on both
 * OkHttp (Android) and Darwin (iOS) engines; Ktor's SSE plugin is not used because its support for
 * custom headers and Darwin is inconsistent.
 *
 * The stream uses [STREAM_TIMEOUT_MS] request timeout (long enough to keep it open for minutes).
 * The caller sets shorter read timeouts via the pacing layer.
 */
@Suppress("TooGenericExceptionCaught")
suspend fun ApiClient.openSseStream(
    path: String,
    lastEventId: String? = null,
    block: HttpRequestBuilder.() -> Unit = {},
): SseConnection =
    try {
        val statement: HttpStatement =
            client.prepareGet {
                url(path.removePrefix("/"))
                accept(ContentType.Text.EventStream)
                lastEventId?.let { header("Last-Event-ID", it) }
                timeout { requestTimeoutMillis = STREAM_TIMEOUT_MS }
                block()
            }
        statement.execute { response ->
            if (response.status.isSuccess()) {
                SseConnection.Stream(readSseFlow(response))
            } else {
                SseConnection.HttpError(
                    status = response.status.value,
                    problem = readProblem(response),
                    retryAfterSeconds = readRetryAfter(response),
                )
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        SseConnection.Failure(failure)
    }

private fun readSseFlow(response: HttpResponse): Flow<SseEvent> =
    flow {
        val channel: ByteReadChannel = response.bodyAsChannel()
        val parser =
            SseParser { event ->
                throw EmitEvent(event)
            }
        while (!channel.isClosedForRead) {
            val line =
                try {
                    channel.readUTF8Line()
                } catch (emit: EmitEvent) {
                    throw emit
                } catch (_: Exception) {
                    null
                }
            if (line == null) break
            try {
                parser.feed(line)
            } catch (emit: EmitEvent) {
                emit(emit.event)
            }
        }
    }

private class EmitEvent(val event: SseEvent) : Throwable()

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private suspend fun readProblem(response: HttpResponse): ProblemDetails? {
    val isProblem = response.contentType()?.match(ContentType.Application.ProblemJson) == true
    val declaredLength = response.contentLength()
    return if (isProblem && (declaredLength == null || declaredLength <= MAX_PROBLEM_BYTES)) {
        try {
            response.body<ProblemDetails>()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    } else {
        null
    }
}

private fun readRetryAfter(response: HttpResponse): Int? =
    response.headers[HttpHeaders.RetryAfter]?.trim()?.toIntOrNull()?.takeIf { it >= 0 }

/** Stream timeout: long enough to keep the connection open for minutes. */
const val STREAM_TIMEOUT_MS = 15L * 60 * 1000 // 15 minutes
