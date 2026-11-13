package com.rusuh.app.crash

import kotlin.test.Test
import kotlin.test.assertEquals

class MessageScrubberTest {
    @Test
    fun `scrubs email addresses`() {
        val input = "Failed for user@example.com"
        val result = scrubCrashMessage(input)
        assertEquals("Failed for [redacted-email]", result)
    }

    @Test
    fun `scrubs multiple email addresses`() {
        val input = "alice@test.com and bob@example.org"
        val result = scrubCrashMessage(input)
        assertEquals("[redacted-email] and [redacted-email]", result)
    }

    @Test
    fun `scrubs 16 digit numbers`() {
        val input = "Card 4111111111111111 failed"
        val result = scrubCrashMessage(input)
        assertEquals("Card [redacted-number] failed", result)
    }

    @Test
    fun `scrubs JWT-like tokens`() {
        val token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"
        val input = "Token $token"
        val result = scrubCrashMessage(input)
        assertEquals("Token [redacted-token]", result)
    }

    @Test
    fun `returns empty string for null`() {
        val result = scrubCrashMessage(null)
        assertEquals("", result)
    }

    @Test
    fun `preserves non-sensitive text`() {
        val input = "Queue closed for event_123"
        val result = scrubCrashMessage(input)
        assertEquals("Queue closed for event_123", result)
    }

    @Test
    fun `does not redact short numbers`() {
        val input = "Error code 12345"
        val result = scrubCrashMessage(input)
        assertEquals("Error code 12345", result)
    }
}
