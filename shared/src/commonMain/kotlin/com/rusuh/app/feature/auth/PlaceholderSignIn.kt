package com.rusuh.app.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val TICK_MILLIS = 1_000L
private const val PLACEHOLDER_ATTEMPTS = 5
private const val PLACEHOLDER_NEW_ACCOUNT_CODE = "123456"
private const val PLACEHOLDER_EXPIRED_CODE = "000000"

/**
 * Local-only stand-in for the sign-in flow so the sheet can be reviewed on a device until
 * AUTH-00-M3 (auth state) and EPIC-01-M6 (API) land. No network, no persistence.
 *
 * Demo rules: after entering an email and a code, "123456" continues to the new-account
 * step, "000000" shows the expired step, any other code is wrong (5 attempts, then expired).
 * Creating the account or tapping outside the sheet closes it.
 *
 * Demo state lives in `remember`, so it is NOT saved across rotation or process death.
 */
@Stable
class PlaceholderSignIn {
    /** The sheet state, or null while the sheet is closed. */
    var state: SignInUiState? by mutableStateOf(null)
        private set

    fun open() {
        state = SignInUiState.Email()
    }

    /** Counts the resend cooldown down by one second. */
    internal fun tick() {
        state =
            when (val current = state) {
                is SignInUiState.Code -> current.copy(resendInSeconds = (current.resendInSeconds - 1).coerceAtLeast(0))
                is SignInUiState.WrongCode ->
                    current.copy(resendInSeconds = (current.resendInSeconds - 1).coerceAtLeast(0))
                else -> current
            }
    }

    internal val isCountingDown: Boolean
        get() =
            when (val current = state) {
                is SignInUiState.Code -> current.resendInSeconds > 0
                is SignInUiState.WrongCode -> current.resendInSeconds > 0
                else -> false
            }

    private var attemptsLeft = PLACEHOLDER_ATTEMPTS

    val actions: SignInActions =
        SignInActions(
            onEmailChange = { value -> (state as? SignInUiState.Email)?.let { state = it.copy(email = value) } },
            onSendCode = {
                (state as? SignInUiState.Email)?.takeIf { isPlausibleEmail(it.email) }?.let {
                    attemptsLeft = PLACEHOLDER_ATTEMPTS
                    state = SignInUiState.Code(email = it.email.trim())
                }
            },
            // The first edit after a wrong code goes back to the plain code step (error styling
            // cleared, cooldown kept); the remaining attempts are tracked in [attemptsLeft].
            onCodeChange = { value ->
                state =
                    when (val current = state) {
                        is SignInUiState.Code -> current.copy(code = value)
                        is SignInUiState.WrongCode ->
                            SignInUiState.Code(current.email, value, current.resendInSeconds)
                        else -> current
                    }
            },
            onCodeComplete = ::verify,
            onResendCode = {
                attemptsLeft = PLACEHOLDER_ATTEMPTS
                state =
                    when (val current = state) {
                        is SignInUiState.Code -> SignInUiState.Code(current.email)
                        is SignInUiState.WrongCode -> SignInUiState.Code(current.email)
                        is SignInUiState.CodeExpired -> SignInUiState.Code(current.email)
                        else -> current
                    }
            },
            onChangeEmail = {
                state =
                    when (val current = state) {
                        is SignInUiState.Code -> SignInUiState.Email(current.email)
                        is SignInUiState.WrongCode -> SignInUiState.Email(current.email)
                        is SignInUiState.CodeExpired -> SignInUiState.Email(current.email)
                        is SignInUiState.NewAccount -> SignInUiState.Email(current.email)
                        else -> current
                    }
            },
            onFullNameChange = { value ->
                (state as? SignInUiState.NewAccount)?.let { state = it.copy(fullName = value) }
            },
            onConsentChange = { value ->
                (state as? SignInUiState.NewAccount)?.let { state = it.copy(consentAccepted = value) }
            },
            onCreateAccount = { state = null },
            onDismiss = { state = null },
        )

    private fun verify(code: String) {
        val current = state
        val (email, cooldown) =
            when (current) {
                is SignInUiState.Code -> current.email to current.resendInSeconds
                is SignInUiState.WrongCode -> current.email to current.resendInSeconds
                else -> return
            }
        if (code != PLACEHOLDER_NEW_ACCOUNT_CODE && code != PLACEHOLDER_EXPIRED_CODE) attemptsLeft -= 1
        state =
            when {
                code == PLACEHOLDER_NEW_ACCOUNT_CODE -> SignInUiState.NewAccount(email)
                code == PLACEHOLDER_EXPIRED_CODE || attemptsLeft <= 0 -> SignInUiState.CodeExpired(email)
                else -> SignInUiState.WrongCode(email, code, attemptsLeft, cooldown)
            }
    }
}

/** Remembers a [PlaceholderSignIn] and drives its resend countdown while a code step is open. */
@Composable
fun rememberPlaceholderSignIn(): PlaceholderSignIn {
    val signIn = remember { PlaceholderSignIn() }
    val counting = signIn.isCountingDown
    LaunchedEffect(counting) {
        while (counting) {
            delay(TICK_MILLIS)
            signIn.tick()
        }
    }
    return signIn
}
