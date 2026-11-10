package com.rusuh.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class OtpCodeInputTest {
    @Test
    fun keepsDigitsOnly() {
        assertEquals("123456", sanitizeOtpInput("12-34 56"))
        assertEquals("", sanitizeOtpInput("abc"))
    }

    @Test
    fun capsAtLength() {
        assertEquals("123456", sanitizeOtpInput("1234567890"))
        assertEquals("1234", sanitizeOtpInput("123456", length = 4))
    }

    @Test
    fun dropsNonAsciiDigits() {
        assertEquals("", sanitizeOtpInput("٣٤"))
    }

    @Test
    fun completionFiresOncePerCompletedValue() {
        val tracker = OtpCompletionTracker()
        assertEquals(false, tracker.shouldComplete("12345"))
        assertEquals(true, tracker.shouldComplete("123456"))
        assertEquals(false, tracker.shouldComplete("123456"))
        // Value changed away (backspace) and back.
        tracker.onCurrentValue("12345")
        assertEquals(true, tracker.shouldComplete("123456"))
    }

    @Test
    fun completionRearmsWhenParentClearsValue() {
        val tracker = OtpCompletionTracker()
        assertEquals(true, tracker.shouldComplete("654321"))
        tracker.onCurrentValue("")
        assertEquals(true, tracker.shouldComplete("654321"))
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", sanitizeOtpInput(""))
    }
}
