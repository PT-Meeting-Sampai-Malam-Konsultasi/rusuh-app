package com.rusuh.app.data.repository

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.plugin.queueToken
import com.rusuh.app.data.network.sse.SseConnection
import com.rusuh.app.data.network.sse.SseEvent
import com.rusuh.app.data.network.sse.openSseStream
import com.rusuh.app.data.remote.dto.FrontierDto
import com.rusuh.app.data.remote.dto.QueueTurnDto
import com.rusuh.app.domain.model.ErrorCode
import io.ktor.client.request.header
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/** A parsed event from the queue stream. */
sealed interface QueueStreamEvent {
    data class Frontier(val dto: FrontierDto) : QueueStreamEvent

    data class Turn(val dto: QueueTurnDto) : QueueStreamEvent

    data object Heartbeat : QueueStreamEvent

    /** Server-suggested reconnect delay in milliseconds. */
    data class RetryHint(val millis: Long) : QueueStreamEvent
}

/** Outcome of attempting to open or read the stream. */
sealed interface StreamOutcome {
    /** The stream is healthy and emitting events. */
    data class Healthy(val events: Flow<QueueStreamEvent>) : StreamOutcome

    /** 409 TOO_FAR: position above streamFrom, keep polling. */
    data object TooFar : StreamOutcome

    /** 410 QUEUE_CLOSED, MISSED or EXPIRED: the queue ended. */
    data class Closed(val code: ErrorCode) : StreamOutcome

    /** 503 STREAM_FULL: keep polling, retry after the given seconds. */
    data class StreamFull(val retryAfterSeconds: Int?) : StreamOutcome

    /** 401: device or queue token rejected. */
    data object Unauthorized : StreamOutcome

    /** Connection failed (network, timeout). */
    data class Failed(val cause: Throwable) : StreamOutcome
}

/** Parameters for opening the queue SSE stream. */
internal data class QueueStreamParams(
    val api: ApiClient,
    val eventId: String,
    val queueToken: String,
    val deviceToken: String?,
    val lastEventId: String?,
    val json: Json,
)

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_CONFLICT = 409
private const val HTTP_GONE = 410
private const val HTTP_SERVICE_UNAVAILABLE = 503

/** Opens the queue SSE stream and maps events. */
internal suspend fun openQueueStream(params: QueueStreamParams): StreamOutcome {
    val path = "/queue/${params.eventId.encodeURLPathPart()}/stream"
    val connection =
        params.api.openSseStream(path, params.lastEventId) {
            queueToken(params.queueToken)
            params.deviceToken?.let { header("X-Device-Token", it) }
        }
    return when (connection) {
        is SseConnection.Stream -> wrapStream(connection.events, params.json)
        is SseConnection.HttpError -> mapHttpError(connection)
        is SseConnection.Failure -> StreamOutcome.Failed(connection.cause)
    }
}

private fun wrapStream(
    rawEvents: Flow<SseEvent>,
    json: Json,
): StreamOutcome {
    val mappedFlow =
        flow {
            rawEvents.collect { event ->
                val mapped = mapEvent(event, json)
                if (mapped != null) emit(mapped)
            }
        }
    return StreamOutcome.Healthy(mappedFlow)
}

private fun mapEvent(
    event: SseEvent,
    json: Json,
): QueueStreamEvent? =
    when (event) {
        is SseEvent.Message ->
            when (event.event) {
                "frontier" ->
                    runCatching { json.decodeFromString<FrontierDto>(event.data) }
                        .getOrNull()
                        ?.let { QueueStreamEvent.Frontier(it) }
                "turn" ->
                    runCatching { json.decodeFromString<QueueTurnDto>(event.data) }
                        .getOrNull()
                        ?.let { QueueStreamEvent.Turn(it) }
                else -> null
            }
        is SseEvent.Comment -> if (event.text == "hb") QueueStreamEvent.Heartbeat else null
        is SseEvent.Retry -> QueueStreamEvent.RetryHint(event.millis)
    }

private fun mapHttpError(error: SseConnection.HttpError): StreamOutcome {
    val code = error.problem?.code ?: ErrorCode.UNKNOWN
    return when (error.status) {
        HTTP_UNAUTHORIZED -> StreamOutcome.Unauthorized
        HTTP_CONFLICT -> StreamOutcome.TooFar
        HTTP_GONE -> StreamOutcome.Closed(code)
        HTTP_SERVICE_UNAVAILABLE -> StreamOutcome.StreamFull(error.retryAfterSeconds)
        else -> StreamOutcome.Failed(Exception("HTTP ${error.status}"))
    }
}
