package com.rusuh.app.feature.auth

import androidx.compose.runtime.Immutable
import com.rusuh.app.mvi.MviEffect
import com.rusuh.app.mvi.MviIntent
import com.rusuh.app.mvi.MviState

/**
 * The code that was sent and the deadlines of its countdowns, in epoch millis of the app's `ServerClock` (never the
 * device clock). [wrongAttempts] counts the wrong codes entered on it, for the rare answer without `attemptsLeft`.
 */
@Immutable
data class PendingChallenge(
    val challengeId: String,
    val validUntilMillis: Long,
    val resendAtMillis: Long,
    val wrongAttempts: Int = 0,
) {
    override fun toString(): String = "PendingChallenge(<redacted>)"
}

/**
 * State of the sign-in flow. [step] is what the screen renders (the Figma states); the rest is flow bookkeeping.
 *
 * @property isBusy a request is in flight: the main button is disabled and further intents are ignored.
 * @property error one inline line in the current step.
 * @property retryAtMillis end of a 429 countdown on the server clock (the error then carries the seconds left).
 * @property newAccountUserId the account the NewAccount step belongs to (its name is stored for this id).
 * @property isFinished login or register is complete (the session is signed in and `SignedIn` was sent).
 */
@Immutable
data class SignInState(
    val step: SignInUiState = SignInUiState.Email(),
    val isBusy: Boolean = false,
    val error: SignInError? = null,
    val challenge: PendingChallenge? = null,
    val retryAtMillis: Long? = null,
    val newAccountUserId: String? = null,
    val isFinished: Boolean = false,
) : MviState {
    val status: SignInStatus get() = SignInStatus(isBusy = isBusy, error = error)

    /**
     * True while the user is signed in but still has to give a name: the Account tab must keep showing the
     * sign-in step instead of the profile page.
     */
    val holdsAccountPage: Boolean get() = step is SignInUiState.NewAccount && !isFinished
}

sealed interface SignInIntent : MviIntent {
    data class EmailChanged(val value: String) : SignInIntent

    data object SendCode : SignInIntent

    data class CodeChanged(val value: String) : SignInIntent

    data class SubmitCode(val code: String) : SignInIntent

    data object ResendCode : SignInIntent

    data object ChangeEmail : SignInIntent

    data class NameChanged(val value: String) : SignInIntent

    data class ConsentChanged(val accepted: Boolean) : SignInIntent

    data object ContinueNewAccount : SignInIntent
}

sealed interface SignInEffect : MviEffect {
    /**
     * Login or register is complete and the session is signed in: the full-screen route closes; the Account tab
     * needs nothing (it follows the session). [isNewAccount] tells whether the account was just created.
     */
    data class SignedIn(val isNewAccount: Boolean) : SignInEffect
}
