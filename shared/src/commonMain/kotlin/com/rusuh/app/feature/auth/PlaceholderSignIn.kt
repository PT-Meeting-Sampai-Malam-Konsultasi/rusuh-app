package com.rusuh.app.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val TICK_MILLIS = 1_000L
private const val PLACEHOLDER_NEW_ACCOUNT_CODE = "123456"
private const val PLACEHOLDER_EXPIRED_CODE = "000000"

/**
 * Local-only stand-in for the sign-in flow so the screens can be reviewed on a device until
 * AUTH-00-M3 (auth state) and EPIC-01-M6 (API) land. No network, no persistence. It is the
 * placeholder behind the [SignInUiState] + [SignInActions] seam: #77 replaces this class with a
 * ViewModel exposing the same two things.
 *
 * Demo rules: after entering an email and a code, "123456" continues to the new-account
 * step, "000000" shows the expired step, any other code is wrong ([SignInPolicy.MAX_ATTEMPTS]
 * attempts, then expired). A code also expires when its validity countdown runs out.
 * Creating the account ends the flow: [state] becomes null and [isSignedIn] true.
 *
 * Demo state lives in `remember`, so it is NOT saved across rotation, tab switches or process death.
 */
@Stable
class PlaceholderSignIn(
    startOpen: Boolean = false,
) {
    /** The current step, or null while the flow is closed (dismissed or finished). */
    var state: SignInUiState? by mutableStateOf(if (startOpen) SignInUiState.Email() else null)
        private set

    /** True once "create account" was tapped (demo only; there is no real session yet). */
    var isSignedIn: Boolean by mutableStateOf(false)
        private set

    fun open() {
        state = SignInUiState.Email()
    }

    /** Counts the resend cooldown and the code validity down by one second. */
    internal fun tick() {
        state =
            when (val current = state) {
                is SignInUiState.Code ->
                    if (current.validForSeconds <= 1) {
                        SignInUiState.CodeExpired(current.email, current.code)
                    } else {
                        current.copy(
                            resendInSeconds = (current.resendInSeconds - 1).coerceAtLeast(0),
                            validForSeconds = current.validForSeconds - 1,
                        )
                    }
                is SignInUiState.WrongCode ->
                    if (current.validForSeconds <= 1) {
                        SignInUiState.CodeExpired(current.email, current.code)
                    } else {
                        current.copy(
                            resendInSeconds = (current.resendInSeconds - 1).coerceAtLeast(0),
                            validForSeconds = current.validForSeconds - 1,
                        )
                    }
                else -> current
            }
    }

    /** True while a code is pending: its validity (and maybe the resend cooldown) is counting down. */
    internal val isCountingDown: Boolean
        get() = state is SignInUiState.Code || state is SignInUiState.WrongCode

    internal var attemptsLeft = SignInPolicy.MAX_ATTEMPTS
        private set

    /** Puts back a saved flow (see [PlaceholderSignInSaver]). */
    internal fun restore(
        restoredState: SignInUiState?,
        signedIn: Boolean,
        attempts: Int,
    ) {
        state = restoredState
        isSignedIn = signedIn
        attemptsLeft = attempts
    }

    val actions: SignInActions =
        SignInActions(
            onEmailChange = { value -> (state as? SignInUiState.Email)?.let { state = it.copy(email = value) } },
            onSendCode = {
                (state as? SignInUiState.Email)?.takeIf { isPlausibleEmail(it.email) }?.let {
                    attemptsLeft = SignInPolicy.MAX_ATTEMPTS
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
                            SignInUiState.Code(current.email, value, current.resendInSeconds, current.validForSeconds)
                        else -> current
                    }
            },
            onCodeComplete = ::verify,
            onResendCode = {
                attemptsLeft = SignInPolicy.MAX_ATTEMPTS
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
            onCreateAccount = {
                if (state is SignInUiState.NewAccount) {
                    state = null
                    isSignedIn = true
                }
            },
            onDismiss = { state = null },
        )

    private fun verify(code: String) {
        val current = state
        val (email, timers) =
            when (current) {
                is SignInUiState.Code -> current.email to (current.resendInSeconds to current.validForSeconds)
                is SignInUiState.WrongCode -> current.email to (current.resendInSeconds to current.validForSeconds)
                else -> return
            }
        if (code != PLACEHOLDER_NEW_ACCOUNT_CODE && code != PLACEHOLDER_EXPIRED_CODE) attemptsLeft -= 1
        state =
            when {
                code == PLACEHOLDER_NEW_ACCOUNT_CODE -> SignInUiState.NewAccount(email)
                code == PLACEHOLDER_EXPIRED_CODE || attemptsLeft <= 0 -> SignInUiState.CodeExpired(email, code)
                else -> SignInUiState.WrongCode(email, code, attemptsLeft, timers.first, timers.second)
            }
    }
}

/**
 * Remembers a [PlaceholderSignIn] ([startOpen]: already on the email step) and drives its
 * countdowns while a code step is open.
 */
@Composable
fun rememberPlaceholderSignIn(startOpen: Boolean = false): PlaceholderSignIn {
    // Saveable so a tab switch (or process death) keeps the step the user was on.
    val signIn = rememberSaveable(saver = PlaceholderSignInSaver) { PlaceholderSignIn(startOpen) }
    val counting = signIn.isCountingDown
    LaunchedEffect(counting) {
        while (counting) {
            delay(TICK_MILLIS)
            signIn.tick()
        }
    }
    return signIn
}
