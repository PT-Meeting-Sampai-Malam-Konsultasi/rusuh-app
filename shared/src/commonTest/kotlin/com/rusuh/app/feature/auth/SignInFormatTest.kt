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
    fun formatsCodeValidityStart() {
        assertEquals("5:00", formatResendCountdown(SignInPolicy.CODE_VALID_SECONDS))
        assertEquals("4:51", formatResendCountdown(SignInPolicy.CODE_VALID_SECONDS - 9))
    }

    @Test
    fun policyNumbersAreConsistent() {
        assertEquals(SignInPolicy.CODE_VALID_MINUTES * 60, SignInPolicy.CODE_VALID_SECONDS)
        assertTrue(SignInPolicy.LOCKOUT_MINUTES > 0)
        assertTrue(SignInPolicy.MAX_ATTEMPTS > 1)
        assertTrue(RESEND_COOLDOWN_SECONDS < SignInPolicy.CODE_VALID_SECONDS)
    }

    @Test
    fun primaryButtonFollowsTheStep() {
        assertFalse(SignInUiState.Email("nama").isPrimaryEnabled())
        assertTrue(SignInUiState.Email("nama@email.com").isPrimaryEnabled())
        assertFalse(SignInUiState.Code("a@b.co", code = "12345").isPrimaryEnabled())
        assertTrue(SignInUiState.Code("a@b.co", code = "123456").isPrimaryEnabled())
        assertTrue(SignInUiState.WrongCode("a@b.co", "123456", 2).isPrimaryEnabled())
        assertTrue(SignInUiState.CodeExpired("a@b.co").isPrimaryEnabled())
        assertFalse(SignInUiState.NewAccount("a@b.co", fullName = "  ").isPrimaryEnabled())
        // The marketing checkbox is optional.
        assertTrue(SignInUiState.NewAccount("a@b.co", fullName = "Sari", consentAccepted = false).isPrimaryEnabled())
    }

    @Test
    fun findsPartRanges() {
        assertEquals(listOf(5..9), findPartRanges("Kode a@b.c ok", listOf("a@b.c")))
        assertEquals(emptyList(), findPartRanges("abc", listOf("x", "")))
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
        assertFalse(isPlausibleEmail("a@b..c"))
    }
}
