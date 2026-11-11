package com.rusuh.app.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PlaceholderSignInTest {
    private fun codeStep(): PlaceholderSignIn {
        val signIn = PlaceholderSignIn()
        signIn.open()
        signIn.actions.onEmailChange("nama@email.com")
        signIn.actions.onSendCode()
        return signIn
    }

    @Test
    fun opensOnEmailAndSendsCode() {
        val signIn = codeStep()
        val state = assertIs<SignInUiState.Code>(signIn.state)
        assertEquals("nama@email.com", state.email)
        assertEquals(RESEND_COOLDOWN_SECONDS, state.resendInSeconds)
    }

    @Test
    fun invalidEmailDoesNotSend() {
        val signIn = PlaceholderSignIn()
        signIn.open()
        signIn.actions.onEmailChange("nama")
        signIn.actions.onSendCode()
        assertIs<SignInUiState.Email>(signIn.state)
    }

    @Test
    fun wrongCodeCountsDownAttempts() {
        val signIn = codeStep()
        signIn.actions.onCodeComplete("111111")
        assertEquals(4, assertIs<SignInUiState.WrongCode>(signIn.state).attemptsLeft)
        signIn.actions.onCodeComplete("222222")
        assertEquals(3, assertIs<SignInUiState.WrongCode>(signIn.state).attemptsLeft)
    }

    @Test
    fun expiresAfterFiveWrongAttempts() {
        val signIn = codeStep()
        repeat(4) { signIn.actions.onCodeComplete("11111$it") }
        assertEquals(1, assertIs<SignInUiState.WrongCode>(signIn.state).attemptsLeft)
        signIn.actions.onCodeComplete("999999")
        assertIs<SignInUiState.CodeExpired>(signIn.state)
    }

    @Test
    fun attemptsSurviveEditingAfterWrongCode() {
        val signIn = codeStep()
        signIn.actions.onCodeComplete("111111")
        signIn.actions.onCodeChange("11111")
        assertIs<SignInUiState.Code>(signIn.state)
        signIn.actions.onCodeComplete("222222")
        assertEquals(3, assertIs<SignInUiState.WrongCode>(signIn.state).attemptsLeft)
    }

    @Test
    fun specialCodesRouteToNewAccountAndExpired() {
        val newAccount = codeStep()
        newAccount.actions.onCodeComplete("123456")
        assertIs<SignInUiState.NewAccount>(newAccount.state)
        val expired = codeStep()
        expired.actions.onCodeComplete("000000")
        assertIs<SignInUiState.CodeExpired>(expired.state)
    }

    @Test
    fun tickCountsDownAndStopsAtZero() {
        val signIn = codeStep()
        signIn.tick()
        assertEquals(RESEND_COOLDOWN_SECONDS - 1, assertIs<SignInUiState.Code>(signIn.state).resendInSeconds)
        repeat(RESEND_COOLDOWN_SECONDS + 5) { signIn.tick() }
        assertEquals(0, assertIs<SignInUiState.Code>(signIn.state).resendInSeconds)
        assertEquals(false, signIn.isCountingDown)
    }

    @Test
    fun cooldownCarriesIntoWrongCode() {
        val signIn = codeStep()
        repeat(10) { signIn.tick() }
        signIn.actions.onCodeComplete("111111")
        assertEquals(50, assertIs<SignInUiState.WrongCode>(signIn.state).resendInSeconds)
    }

    @Test
    fun resendRestartsCooldownAndAttempts() {
        val signIn = codeStep()
        repeat(10) { signIn.tick() }
        signIn.actions.onCodeComplete("111111")
        signIn.actions.onResendCode()
        val state = assertIs<SignInUiState.Code>(signIn.state)
        assertEquals(RESEND_COOLDOWN_SECONDS, state.resendInSeconds)
        signIn.actions.onCodeComplete("111111")
        assertEquals(4, assertIs<SignInUiState.WrongCode>(signIn.state).attemptsLeft)
    }

    @Test
    fun resendFromExpiredReturnsToCode() {
        val signIn = codeStep()
        signIn.actions.onCodeComplete("000000")
        signIn.actions.onResendCode()
        assertIs<SignInUiState.Code>(signIn.state)
    }

    @Test
    fun dismissClosesSheet() {
        val signIn = codeStep()
        signIn.actions.onDismiss()
        assertNull(signIn.state)
    }
}
