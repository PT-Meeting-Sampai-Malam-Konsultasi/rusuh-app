package com.rusuh.app.data.network.sse

/**
 * Parses SSE lines according to the WHATWG spec. Accumulates field lines until a blank line dispatches.
 * Call [feed] with each line (no trailing CRLF/LF); [onEvent] fires for each completed event.
 */
class SseParser(
    private val onEvent: (SseEvent) -> Unit,
) {
    private var eventType: String? = null
    private val dataBuffer = StringBuilder()
    private var hasData = false
    private var lastEventId: String? = null
    private var retry: Long? = null

    fun feed(line: String) {
        when {
            line.isEmpty() -> dispatch()
            line.startsWith(":") -> onEvent(SseEvent.Comment(line.drop(1).trimStart()))
            else -> parseLine(line)
        }
    }

    private fun parseLine(line: String) {
        val colonIndex = line.indexOf(':')
        val (field, value) =
            if (colonIndex < 0) {
                line to ""
            } else {
                val f = line.substring(0, colonIndex)
                val v = line.substring(colonIndex + 1).let { if (it.startsWith(" ")) it.drop(1) else it }
                f to v
            }
        when (field) {
            "event" -> eventType = value
            "data" -> {
                if (hasData) dataBuffer.append('\n')
                dataBuffer.append(value)
                hasData = true
            }
            "id" -> if (!value.contains('\u0000')) lastEventId = value
            "retry" -> {
                value.toLongOrNull()?.takeIf { it >= 0 }?.let {
                    retry = it
                    onEvent(SseEvent.Retry(it))
                }
            }
        }
    }

    private fun dispatch() {
        if (!hasData) {
            eventType = null
            return
        }
        onEvent(
            SseEvent.Message(
                event = eventType,
                data = dataBuffer.toString(),
                id = lastEventId,
                retry = retry,
            ),
        )
        dataBuffer.clear()
        hasData = false
        eventType = null
    }

    fun reset() {
        eventType = null
        dataBuffer.clear()
        hasData = false
        lastEventId = null
        retry = null
    }
}
