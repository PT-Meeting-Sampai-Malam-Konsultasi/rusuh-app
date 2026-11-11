package com.rusuh.app.feature.auth

import androidx.compose.runtime.Immutable

/** Seconds before a new code can be requested (ADR-0006). */
const val RESEND_COOLDOWN_SECONDS = 60

/**
 * UI-level render state for the sign-in sheet (Figma Login: Email, Code, Wrong code,
 * Code expired, New account). Presentation-only discriminator, NOT an MVI state;
 * AUTH-00-M3 maps the real auth state onto it. Each case carries the values the fields
 * show, so the sheet stays stateless.
 */
@Immutable
sealed interface SignInUiState {
    /** Ask for the email address the code is sent to. */
    data class Email(
        val email: String = "",
    ) : SignInUiState

    /** A code was sent to [email]; [resendInSeconds] > 0 keeps "resend" disabled. */
    data class Code(
        val email: String,
        val code: String = "",
        val resendInSeconds: Int = RESEND_COOLDOWN_SECONDS,
    ) : SignInUiState

    /** The entered [code] was rejected; [attemptsLeft] tries remain on this code. */
    data class WrongCode(
        val email: String,
        val code: String,
        val attemptsLeft: Int,
        val resendInSeconds: Int = 0,
    ) : SignInUiState

    /** The code is no longer valid (timed out or used up); a new one must be requested. */
    data class CodeExpired(
        val email: String,
    ) : SignInUiState

    /** First sign-in for [email]: ask for a full name and consent before creating the account. */
    data class NewAccount(
        val email: String,
        val fullName: String = "",
        val consentAccepted: Boolean = false,
    ) : SignInUiState
}

/**
 * Callbacks the sign-in sheet raises, bundled like MyQueuesActions. All default to no-ops
 * so previews can render the sheet.
 */
data class SignInActions(
    val onEmailChange: (String) -> Unit = {},
    val onSendCode: () -> Unit = {},
    val onCodeChange: (String) -> Unit = {},
    val onCodeComplete: (String) -> Unit = {},
    val onResendCode: () -> Unit = {},
    val onChangeEmail: () -> Unit = {},
    val onFullNameChange: (String) -> Unit = {},
    val onConsentChange: (Boolean) -> Unit = {},
    val onCreateAccount: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)
