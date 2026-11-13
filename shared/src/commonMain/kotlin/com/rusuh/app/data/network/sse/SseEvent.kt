package com.rusuh.app.data.network.sse

/** A parsed Server-Sent Event (SSE). */
sealed interface SseEvent {
    /** Standard named event with optional data and id. */
    data class Message(
        val event: String?,
        val data: String,
        val id: String?,
        val retry: Long?,
    ) : SseEvent

    /** Server suggested retry interval in milliseconds. */
    data class Retry(val millis: Long) : SseEvent

    /** Comment line (`:` prefix). */
    data class Comment(val text: String) : SseEvent
}
