package com.rusuh.app.data.network.sse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SseParserTest {
    @Test
    fun parsesASimpleFrontierEvent() {
        val events = mutableListOf<SseEvent>()
        val parser = SseParser { events += it }

        parser.feed("event: frontier")
        parser.feed("""data: {"state":"open","served":100}""")
        parser.feed("")

        assertEquals(1, events.size)
        val msg = events.single() as SseEvent.Message
        assertEquals("frontier", msg.event)
        assertEquals("""{"state":"open","served":100}""", msg.data)
    }

    @Test
    fun parsesEventWithIdAndRetry() {
        val events = mutableListOf<SseEvent>()
        val parser = SseParser { events += it }

        parser.feed("retry: 3000")
        parser.feed("event: frontier")
        parser.feed("id: 12345")
        parser.feed("data: {}")
        parser.feed("")

        assertEquals(2, events.size)
        val retry = events[0] as SseEvent.Retry
        assertEquals(3000L, retry.millis)
        val msg = events[1] as SseEvent.Message
        assertEquals("frontier", msg.event)
        assertEquals("12345", msg.id)
        assertEquals(3000L, msg.retry)
    }

    @Test
    fun parsesMultiLineData() {
        val events = mutableListOf<SseEvent>()
        val parser = SseParser { events += it }

        parser.feed("data: line1")
        parser.feed("data: line2")
        parser.feed("data: line3")
        parser.feed("")

        val msg = events.single() as SseEvent.Message
        assertEquals("line1\nline2\nline3", msg.data)
    }

    @Test
    fun parsesCommentLines() {
        val events = mutableListOf<SseEvent>()
        val parser = SseParser { events += it }

        parser.feed(": hb")
        parser.feed(": another comment")

        assertEquals(2, events.size)
        assertEquals("hb", (events[0] as SseEvent.Comment).text)
        assertEquals("another comment", (events[1] as SseEvent.Comment).text)
    }

    @Test
    fun ignoresUnknownFields() {
        val events = mutableListOf<SseEvent>()
        val parser = SseParser { events += it }

        parser.feed("event: test")
        parser.feed("unknown: value")
        parser.feed("data: payload")
        parser.feed("")

        val msg = events.single() as SseEvent.Message
        assertEquals("test", msg.event)
        assertEquals("payload", msg.data)
    }

    @Test
    fun emptyDataBufferDoesNotDispatch() {
        val events = mutableListOf<SseEvent>()
        val parser = SseParser { events += it }

        parser.feed("event: test")
        parser.feed("")

        assertTrue(events.isEmpty())
    }

    @Test
    fun handlesColonInValue() {
        val events = mutableListOf<SseEvent>()
        val parser = SseParser { events += it }

        parser.feed("data: time:12:30:00")
        parser.feed("")

        val msg = events.single() as SseEvent.Message
        assertEquals("time:12:30:00", msg.data)
    }

    @Test
    fun handlesNoColonInLine() {
        val events = mutableListOf<SseEvent>()
        val parser = SseParser { events += it }

        parser.feed("data")
        parser.feed("")

        val msg = events.single() as SseEvent.Message
        assertEquals("", msg.data)
    }

    @Test
    fun trimsLeadingSpaceFromValue() {
        val events = mutableListOf<SseEvent>()
        val parser = SseParser { events += it }

        parser.feed("data:  two spaces")
        parser.feed("")

        val msg = events.single() as SseEvent.Message
        assertEquals(" two spaces", msg.data)
    }

    @Test
    fun retryMustBeNumeric() {
        val events = mutableListOf<SseEvent>()
        val parser = SseParser { events += it }

        parser.feed("retry: abc")
        parser.feed("data: test")
        parser.feed("")

        assertEquals(1, events.size)
        val msg = events.single() as SseEvent.Message
        assertEquals(null, msg.retry)
    }

    @Test
    fun lastEventIdPersistsAcrossDispatches() {
        val events = mutableListOf<SseEvent>()
        val parser = SseParser { events += it }

        parser.feed("id: first")
        parser.feed("data: one")
        parser.feed("")
        parser.feed("data: two")
        parser.feed("")

        assertEquals(2, events.size)
        assertEquals("first", (events[0] as SseEvent.Message).id)
        assertEquals("first", (events[1] as SseEvent.Message).id)
    }

    @Test
    fun idWithNullByteIsIgnored() {
        val events = mutableListOf<SseEvent>()
        val parser = SseParser { events += it }

        parser.feed("id: has\u0000null")
        parser.feed("data: test")
        parser.feed("")

        val msg = events.single() as SseEvent.Message
        assertEquals(null, msg.id)
    }

    @Test
    fun resetClearsState() {
        val events = mutableListOf<SseEvent>()
        val parser = SseParser { events += it }

        parser.feed("id: old")
        parser.feed("event: partial")
        parser.reset()
        parser.feed("data: fresh")
        parser.feed("")

        val msg = events.single() as SseEvent.Message
        assertEquals(null, msg.event)
        assertEquals(null, msg.id)
    }
}
