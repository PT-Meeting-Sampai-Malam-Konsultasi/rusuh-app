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
        val first = assertIs<SignInUiState.Code>(signIn.state)
        assertEquals(RESEND_COOLDOWN_SECONDS - 1, first.resendInSeconds)
        assertEquals(SignInPolicy.CODE_VALID_SECONDS - 1, first.validForSeconds)
        repeat(RESEND_COOLDOWN_SECONDS + 5) { signIn.tick() }
        assertEquals(0, assertIs<SignInUiState.Code>(signIn.state).resendInSeconds)
        assertEquals(true, signIn.isCountingDown)
    }

    @Test
    fun codeExpiresWhenValidityRunsOut() {
        val signIn = codeStep()
        repeat(SignInPolicy.CODE_VALID_SECONDS) { signIn.tick() }
        assertIs<SignInUiState.CodeExpired>(signIn.state)
        assertEquals(false, signIn.isCountingDown)
    }

    @Test
    fun validityCarriesIntoWrongCodeAndBack() {
        val signIn = codeStep()
        repeat(10) { signIn.tick() }
        signIn.actions.onCodeComplete("111111")
        val wrong = assertIs<SignInUiState.WrongCode>(signIn.state)
        assertEquals(SignInPolicy.CODE_VALID_SECONDS - 10, wrong.validForSeconds)
        signIn.actions.onCodeChange("11111")
        assertEquals(SignInPolicy.CODE_VALID_SECONDS - 10, assertIs<SignInUiState.Code>(signIn.state).validForSeconds)
    }

    @Test
    fun wrongCodeExpiresWhenValidityRunsOutAndKeepsTheCode() {
        val signIn = codeStep()
        signIn.actions.onCodeComplete("111111")
        assertIs<SignInUiState.WrongCode>(signIn.state)
        repeat(SignInPolicy.CODE_VALID_SECONDS) { signIn.tick() }
        assertEquals("111111", assertIs<SignInUiState.CodeExpired>(signIn.state).code)
    }

    @Test
    fun expiredStepCarriesTheLastCode() {
        val special = codeStep()
        special.actions.onCodeComplete("000000")
        assertEquals("000000", assertIs<SignInUiState.CodeExpired>(special.state).code)
        val attempts = codeStep()
        repeat(SignInPolicy.MAX_ATTEMPTS) { attempts.actions.onCodeComplete("99999$it") }
        assertEquals("999994", assertIs<SignInUiState.CodeExpired>(attempts.state).code)
        val timedOut = codeStep()
        timedOut.actions.onCodeChange("4820")
        repeat(SignInPolicy.CODE_VALID_SECONDS) { timedOut.tick() }
        assertEquals("4820", assertIs<SignInUiState.CodeExpired>(timedOut.state).code)
    }

    @Test
    fun changeEmailFromNewAccountAndExpiredKeepsTheEmail() {
        val newAccount = codeStep()
        newAccount.actions.onCodeComplete("123456")
        newAccount.actions.onChangeEmail()
        assertEquals("nama@email.com", assertIs<SignInUiState.Email>(newAccount.state).email)
        val expired = codeStep()
        expired.actions.onCodeComplete("000000")
        expired.actions.onChangeEmail()
        assertEquals("nama@email.com", assertIs<SignInUiState.Email>(expired.state).email)
    }

    @Test
    fun createAccountOutsideNewAccountDoesNotSignIn() {
        val signIn = codeStep()
        signIn.actions.onCreateAccount()
        assertIs<SignInUiState.Code>(signIn.state)
        assertEquals(false, signIn.isSignedIn)
    }

    @Test
    fun savedFlowSurvivesEveryStep() {
        val states =
            listOf(
                null,
                SignInUiState.Email("a@b.co"),
                SignInUiState.Code("a@b.co", "123", 42, 291),
                SignInUiState.WrongCode("a@b.co", "654321", 2, 7, 280),
                SignInUiState.CodeExpired("a@b.co", "482019"),
                SignInUiState.NewAccount("a@b.co", "Sari", true),
            )
        states.forEach { assertEquals(it, decodeSignInState(encodeSignInState(it))) }
    }

    @Test
    fun creatingTheAccountEndsTheFlowSignedIn() {
        val signIn = codeStep()
        signIn.actions.onCodeComplete("123456")
        signIn.actions.onFullNameChange("Sari Wulandari")
        assertEquals(false, signIn.isSignedIn)
        signIn.actions.onCreateAccount()
        assertNull(signIn.state)
        assertEquals(true, signIn.isSignedIn)
    }

    @Test
    fun startOpenBeginsOnEmail() {
        assertIs<SignInUiState.Email>(PlaceholderSignIn(startOpen = true).state)
        assertNull(PlaceholderSignIn().state)
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
    fun dismissClosesFlow() {
        val signIn = codeStep()
        signIn.actions.onDismiss()
        assertNull(signIn.state)
    }
}
