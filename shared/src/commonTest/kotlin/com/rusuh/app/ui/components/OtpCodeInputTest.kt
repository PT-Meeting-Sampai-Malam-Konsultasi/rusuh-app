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
    fun emptyStaysEmpty() {
        assertEquals("", sanitizeOtpInput(""))
    }
}
