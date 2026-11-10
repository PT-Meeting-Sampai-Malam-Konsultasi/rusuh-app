package com.rusuh.app.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignInFormatTest {
    @Test
    fun formatsCountdown() {
        assertEquals("1:00", formatResendCountdown(60))
        assertEquals("0:42", formatResendCountdown(42))
        assertEquals("0:07", formatResendCountdown(7))
        assertEquals("0:00", formatResendCountdown(0))
    }

    @Test
    fun clampsNegativeCountdown() {
        assertEquals("0:00", formatResendCountdown(-5))
    }

    @Test
    fun acceptsPlausibleEmails() {
        assertTrue(isPlausibleEmail("nama@email.com"))
        assertTrue(isPlausibleEmail("  nama@mail.co.id "))
    }

    @Test
    fun rejectsImplausibleEmails() {
        assertFalse(isPlausibleEmail(""))
        assertFalse(isPlausibleEmail("nama"))
        assertFalse(isPlausibleEmail("nama@"))
        assertFalse(isPlausibleEmail("@email.com"))
        assertFalse(isPlausibleEmail("nama@email"))
        assertFalse(isPlausibleEmail("nama@email."))
        assertFalse(isPlausibleEmail("a b@email.com"))
        assertFalse(isPlausibleEmail("a@@email.com"))
    }
}
