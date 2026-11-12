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

    // Pins the numbers the copy shows. The strings take them as placeholders, so a change here
    // must be a deliberate decision (design says 5 and 15 minutes, ADR-0006 says 10 minutes valid).
    @Test
    fun policyNumbersMatchTheDesignUntilTheTeamDecides() {
        assertEquals(5, SignInPolicy.CODE_VALID_MINUTES)
        assertEquals(15, SignInPolicy.LOCKOUT_MINUTES)
        assertEquals(5, SignInPolicy.MAX_ATTEMPTS)
        assertEquals(60, RESEND_COOLDOWN_SECONDS)
        assertEquals(300, SignInPolicy.CODE_VALID_SECONDS)
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
        // A repeated part takes the next occurrence, not the same one twice.
        assertEquals(listOf(0..1, 3..4), findPartRanges("ab ab", listOf("ab", "ab")))
        assertEquals(listOf(0..1), findPartRanges("ab", listOf("ab", "ab")))
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
