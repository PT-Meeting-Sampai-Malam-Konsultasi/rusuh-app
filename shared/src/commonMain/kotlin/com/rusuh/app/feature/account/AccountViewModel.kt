package com.rusuh.app.feature.account

import com.rusuh.app.analytics.Analytics
import com.rusuh.app.analytics.AnalyticsConsent
import com.rusuh.app.analytics.AnalyticsConsentStore
import com.rusuh.app.analytics.AnalyticsEvents
import com.rusuh.app.crash.CrashReporter
import com.rusuh.app.crash.NoOpCrashReporter
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
@Suppress("LongParameterList") // ViewModel with DI-injected dependencies.
class AccountViewModel(
    private val getProfile: GetProfileUseCase,
    private val signOut: SignOutUseCase,
    private val observeSession: ObserveSessionUseCase,
    private val consentStore: AnalyticsConsentStore,
    private val analytics: Analytics,
    private val crashReporter: CrashReporter = NoOpCrashReporter,
    private val allowDebugOptions: Boolean = false,
) : MviViewModel<AccountState, AccountIntent, AccountEffect>(
        AccountState(
            phase = observeSession.currentPhase,
            analyticsConsent = consentStore.consent.value,
            allowDebugOptions = allowDebugOptions,
        ),
    ) {
    private var heldByNameStep = false
    private var profileLoadGeneration = 0

    init {
        viewModelScope.launch { observeSession.phase().collect { onPhase(it) } }
        viewModelScope.launch { observeSession.expired().collect { setState { copy(sessionExpiredNotice = true) } } }
        viewModelScope.launch { consentStore.consent.collect { setState { copy(analyticsConsent = it) } } }
        viewModelScope.launch {
            if (!observeSession.awaitSignedIn()) {
                setState { if (phase == SessionPhase.Restoring) copy(phase = SessionPhase.SignedOut) else this }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod") // MVI reduce with many intents.
    override suspend fun reduce(intent: AccountIntent) {
        when (intent) {
            AccountIntent.LoadProfile ->
                if (currentState.profile == ProfileLoad.Idle || currentState.profile == ProfileLoad.Failed) load()
            AccountIntent.RetryProfile -> if (currentState.profile != ProfileLoad.Loading) load()
            AccountIntent.SignOut -> signOutNow()
            AccountIntent.TestCrash ->
                @Suppress("TooGenericExceptionThrown") // Intentional test crash for Crashlytics verification.
                if (allowDebugOptions) throw RuntimeException("Crashlytics test crash")
            AccountIntent.TestNonFatal ->
                if (allowDebugOptions) {
                    crashReporter.recordNonFatal(
                        RuntimeException("Crashlytics test non-fatal"),
                    )
                }
            is AccountIntent.NameStepVisibilityChanged -> {
                if (heldByNameStep && !intent.held) {
                    profileLoadGeneration++
                    setState { copy(profile = ProfileLoad.Idle) }
                }
                heldByNameStep = intent.held
            }
            is AccountIntent.SetAnalyticsConsent -> setAnalyticsConsent(intent.enabled)
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
        if (phase == SessionPhase.SignedOut) {
            analytics.setUserId(null)
            analytics.setUserProperty(AnalyticsEvents.SIGNED_IN, "false")
        } else if (phase == SessionPhase.SignedIn) {
            analytics.setUserProperty(AnalyticsEvents.SIGNED_IN, "true")
        }
    }

    private suspend fun load() {
        if (currentState.phase != SessionPhase.SignedIn) return
        val generation = ++profileLoadGeneration
        setState { copy(profile = ProfileLoad.Loading) }
        val result = getProfile()
        if (currentState.phase != SessionPhase.SignedIn || generation != profileLoadGeneration) return
        if (result is DomainResult.Success) analytics.setUserId(result.data.userId)
        setState {
            copy(profile = if (result is DomainResult.Success) ProfileLoad.Loaded(result.data) else ProfileLoad.Failed)
        }
    }

    private suspend fun signOutNow() {
        if (currentState.isSigningOut || currentState.phase != SessionPhase.SignedIn) return
        setState { copy(isSigningOut = true) }
        analytics.setUserId(null)
        analytics.setUserProperty(AnalyticsEvents.SIGNED_IN, "false")
        signOut()
        setState { copy(isSigningOut = false) }
    }

    private suspend fun setAnalyticsConsent(enabled: Boolean) {
        val consent = if (enabled) AnalyticsConsent.Granted else AnalyticsConsent.Denied
        consentStore.set(consent)
        analytics.setCollectionEnabled(enabled)
    }
}
