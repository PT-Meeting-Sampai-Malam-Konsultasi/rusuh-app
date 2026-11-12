package com.rusuh.app.feature.auth

import androidx.compose.runtime.Immutable

/** Seconds before a new code can be requested (ADR-0006). */
const val RESEND_COOLDOWN_SECONDS = 60

private const val SECONDS_PER_MINUTE = 60

/**
 * Numbers that appear in the sign-in copy and countdowns, in one place so they can change
 * together. The string resources take them as placeholders, never as literals.
 *
 * TODO(AUTH-00 #77): the Figma copy says "5 menit" (code validity) and "15 menit" (lock-out
 *  after wrong codes), ADR-0006 says 10 minutes valid, a new code after 60 s and 5 attempts.
 *  Keep the design numbers until the team confirms, then change them here only.
 */
object SignInPolicy {
    /** Minutes a code stays valid, as shown to the user (design: 5, ADR-0006: 10). */
    const val CODE_VALID_MINUTES = 5

    /** Seconds a code stays valid; the "Kode berlaku" countdown starts here. */
    const val CODE_VALID_SECONDS = CODE_VALID_MINUTES * SECONDS_PER_MINUTE

    /** Minutes to wait for a new code after the attempts run out, as shown to the user (design: 15). */
    const val LOCKOUT_MINUTES = 15

    /** Wrong codes allowed on one code (ADR-0006). */
    const val MAX_ATTEMPTS = 5

    /** Sender shown in the "not in your inbox" hint (synthetic until the mail domain is confirmed). */
    const val SENDER_ADDRESS = "no-reply@rusuh.id"
}

/**
 * UI-level render state for the sign-in screens (Figma Login: Email, Code, Wrong code,
 * Code expired, New account). Presentation-only discriminator, NOT an MVI state;
 * AUTH-00-M3 maps the real auth state onto it. Each case carries the values the fields
 * show, so the screens stay stateless.
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
        val validForSeconds: Int = SignInPolicy.CODE_VALID_SECONDS,
    ) : SignInUiState

    /** The entered [code] was rejected; [attemptsLeft] tries remain on this code. */
    data class WrongCode(
        val email: String,
        val code: String,
        val attemptsLeft: Int,
        val resendInSeconds: Int = 0,
        val validForSeconds: Int = SignInPolicy.CODE_VALID_SECONDS,
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

/** Where the sign-in content is shown; decides what happens after the new-account step. */
enum class SignInVariant {
    /** Full screen from the waiting room: after signing up the user continues to the queue. */
    Full,

    /** Account tab: no queue to continue to, so the last button is a plain "Lanjut". */
    Compact,
}

/**
 * The event the user continues to after signing in, shown in the banner of the full login.
 * Plain display strings so the screen does not depend on a domain model; #103 (waiting room
 * gate) fills them from the queue's event.
 *
 * @property openingTime opening time as already formatted for display, e.g. "10.00 WIB".
 */
data class SignInEventContext(
    val name: String,
    val tag: String,
    val openingTime: String,
)

/**
 * Callbacks the sign-in screens raise, bundled like MyQueuesActions. All default to no-ops
 * so previews can render them. This and [SignInUiState] are the seam AUTH-00-M3 (#77) fills
 * with a real ViewModel.
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
    /** Back arrow of the full screen (the compact Account-tab version has none). */
    val onDismiss: () -> Unit = {},
)
