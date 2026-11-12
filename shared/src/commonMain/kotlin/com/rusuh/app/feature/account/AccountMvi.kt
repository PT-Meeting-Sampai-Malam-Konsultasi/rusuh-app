package com.rusuh.app.feature.account

import androidx.compose.runtime.Immutable
import com.rusuh.app.domain.model.AccountSummary
import com.rusuh.app.domain.session.SessionPhase
import com.rusuh.app.mvi.MviEffect
import com.rusuh.app.mvi.MviIntent
import com.rusuh.app.mvi.MviState

/** Where the profile (`GET /me`) is. */
@Immutable
sealed interface ProfileLoad {
    data object Idle : ProfileLoad

    data object Loading : ProfileLoad

    data class Loaded(val profile: AccountSummary) : ProfileLoad

    data object Failed : ProfileLoad
}

/**
 * State of the Account tab: the session phase, the profile load and whether the last sign-out was not the user's
 * ([sessionExpiredNotice]: the refresh token was rejected).
 */
@Immutable
data class AccountState(
    val phase: SessionPhase = SessionPhase.Restoring,
    val profile: ProfileLoad = ProfileLoad.Idle,
    val sessionExpiredNotice: Boolean = false,
    val isSigningOut: Boolean = false,
) : MviState

sealed interface AccountIntent : MviIntent {
    /** Load the profile when signed in and none is loaded or loading (idempotent). */
    data object LoadProfile : AccountIntent

    /** Load it again after a failure. */
    data object RetryProfile : AccountIntent

    data object SignOut : AccountIntent

    data class NameStepVisibilityChanged(val held: Boolean) : AccountIntent
}

/** No one-time effects yet: sign-out is shown by the session phase changing. */
sealed interface AccountEffect : MviEffect

/** What the Account tab body shows. */
@Immutable
sealed interface AccountBody {
    /** Cold start, the stored session is being read: neutral, never the login. */
    data object Restoring : AccountBody

    /** The compact login; [sessionExpired] adds the "session ended" line. */
    data class SignedOut(val sessionExpired: Boolean) : AccountBody

    data object ProfileLoading : AccountBody

    data class Profile(val profile: AccountProfileUi) : AccountBody

    data object ProfileError : AccountBody
}

/**
 * Maps the state to the body. [signInHoldsAccountPage] is true while the new-account step is open (signed in, but
 * the name step comes first), so the login stays on screen instead of flashing the profile.
 */
fun AccountState.toBody(signInHoldsAccountPage: Boolean = false): AccountBody =
    when (phase) {
        SessionPhase.Restoring -> AccountBody.Restoring
        SessionPhase.SignedOut -> AccountBody.SignedOut(sessionExpired = sessionExpiredNotice)
        SessionPhase.SignedIn ->
            when {
                signInHoldsAccountPage -> AccountBody.SignedOut(sessionExpired = false)
                else ->
                    when (profile) {
                        ProfileLoad.Idle, ProfileLoad.Loading -> AccountBody.ProfileLoading
                        is ProfileLoad.Loaded -> AccountBody.Profile(profile.profile.toUi())
                        ProfileLoad.Failed -> AccountBody.ProfileError
                    }
            }
    }

/** The page model: the server name, else the device-only one, else the part of the email before the "@". */
fun AccountSummary.toUi(): AccountProfileUi =
    AccountProfileUi(
        name = name?.takeIf { it.isNotBlank() } ?: email.substringBefore('@'),
        email = email,
        // Every account signs in with a code sent to its email, so a signed-in email is verified.
        emailVerified = true,
    )
