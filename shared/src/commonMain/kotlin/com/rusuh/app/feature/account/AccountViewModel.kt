package com.rusuh.app.feature.account

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.session.SessionPhase
import com.rusuh.app.domain.usecase.GetProfileUseCase
import com.rusuh.app.domain.usecase.ObserveSessionUseCase
import com.rusuh.app.domain.usecase.SignOutUseCase
import com.rusuh.app.mvi.MviViewModel
import kotlinx.coroutines.launch

/**
 * MVI ViewModel of the Account tab (ACCOUNT-00 #104): follows the session, loads `GET /me` once signed in, and signs
 * out. The login itself is `SignInViewModel`; the route combines the two with [toBody].
 *
 * - Cold start: [SessionPhase.Restoring] until the stored session is read (bounded at 5 s by `awaitSignedIn`, then
 *   signed out), so the wrong screen never flashes.
 * - The profile is loaded on [AccountIntent.LoadProfile] (the route sends it once the page may be shown, i.e. not
 *   during the new-account step, so the device-only name is already stored). Signing out or an expired session drops
 *   it.
 */
class AccountViewModel(
    private val getProfile: GetProfileUseCase,
    private val signOut: SignOutUseCase,
    private val observeSession: ObserveSessionUseCase,
) : MviViewModel<AccountState, AccountIntent, AccountEffect>(AccountState()) {
    init {
        viewModelScope.launch { observeSession.phase().collect { onPhase(it) } }
        viewModelScope.launch { observeSession.expired().collect { setState { copy(sessionExpiredNotice = true) } } }
        viewModelScope.launch {
            // A storage read that hangs must not leave the tab on the neutral state: fall back to signed out.
            // (A true answer is delivered by the phase flow; only "not signed in" can be the timeout.)
            if (!observeSession.awaitSignedIn()) {
                setState { if (phase == SessionPhase.Restoring) copy(phase = SessionPhase.SignedOut) else this }
            }
        }
    }

    override suspend fun reduce(intent: AccountIntent) {
        when (intent) {
            AccountIntent.LoadProfile ->
                if (currentState.profile == ProfileLoad.Idle || currentState.profile == ProfileLoad.Failed) load()
            AccountIntent.RetryProfile -> if (currentState.profile != ProfileLoad.Loading) load()
            AccountIntent.SignOut -> signOutNow()
        }
    }

    private fun onPhase(phase: SessionPhase) {
        setState {
            copy(
                phase = phase,
                profile = if (phase == SessionPhase.SignedIn) profile else ProfileLoad.Idle,
                sessionExpiredNotice = if (phase == SessionPhase.SignedIn) false else sessionExpiredNotice,
                isSigningOut = isSigningOut && phase == SessionPhase.SignedIn,
            )
        }
    }

    private suspend fun load() {
        if (currentState.phase != SessionPhase.SignedIn) return
        setState { copy(profile = ProfileLoad.Loading) }
        val result = getProfile()
        // Signed out (or a different login) while waiting: the answer is not for this screen any more.
        if (currentState.phase != SessionPhase.SignedIn) return
        setState {
            copy(profile = if (result is DomainResult.Success) ProfileLoad.Loaded(result.data) else ProfileLoad.Failed)
        }
    }

    private suspend fun signOutNow() {
        if (currentState.isSigningOut || currentState.phase != SessionPhase.SignedIn) return
        setState { copy(isSigningOut = true) }
        // The local session is cleared first (the phase flips at once); the result only describes the server revoke.
        signOut()
        setState { copy(isSigningOut = false) }
    }
}
