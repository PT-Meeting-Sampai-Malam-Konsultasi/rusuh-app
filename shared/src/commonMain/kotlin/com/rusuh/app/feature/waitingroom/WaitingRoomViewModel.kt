package com.rusuh.app.feature.waitingroom

import com.rusuh.app.data.network.session.SessionStateProvider
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.SignInRequiredException
import com.rusuh.app.domain.usecase.LeaveQueueUseCase
import com.rusuh.app.feature.waitingroom.domain.model.QueuePlace
import com.rusuh.app.feature.waitingroom.domain.model.QueueState
import com.rusuh.app.feature.waitingroom.domain.usecase.ObserveQueuePlaceUseCase
import com.rusuh.app.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * MVI ViewModel for the Waiting Room of one event.
 *
 * Observes the queue place through [ObserveQueuePlaceUseCase] and reduces it into
 * [WaitingRoomState]; user [WaitingRoomIntent]s become [WaitingRoomEffect]s.
 *
 * Countdowns run on server time: each snapshot is anchored to [monotonicMillis] when it
 * arrives, and [WaitingRoomIntent.Tick] re-derives them as `serverNow + (now - receivedAt)`.
 * The device wall clock is never read.
 *
 * ## Login gate (AUTH-00-M6 #103)
 *
 * A guest can wait in the queue. When the turn arrives:
 * - Signed-in users go straight to Detail (auto-advance or the manual "Choose tickets now" tap).
 * - Guests are HELD in the waiting room on a `YourTurn(heldForSignIn = true)` state: the
 *   admission countdown runs (from the cached `AdmissionInfo.expiresAt`, never the device clock)
 *   and a sign-in prompt replaces the "choose tickets" button. After a successful sign-in
 *   (SessionManager.isSignedIn becomes true, the sign-in route pops back) the user continues
 *   to Detail with the remaining time. If the countdown ends while held, the turn is lost
 *   (MissedTurn state).
 *
 * @param monotonicMillis monotonic milliseconds; injectable so tests can fake time.
 * @param sessionState provides the signed-in state for guest vs signed-in branching.
 */
class WaitingRoomViewModel(
    private val eventId: String,
    private val observeQueuePlace: ObserveQueuePlaceUseCase,
    private val leaveQueue: LeaveQueueUseCase,
    private val monotonicMillis: () -> Long = defaultMonotonicMillis(),
    private val sessionState: SessionStateProvider? = null,
) : MviViewModel<WaitingRoomState, WaitingRoomIntent, WaitingRoomEffect>(WaitingRoomState()) {
    private var observeJob: Job? = null
    private var lastPlace: QueuePlace? = null
    private var receivedAtMillis: Long = 0L
    private var backSent = false
    private var sessionJob: Job? = null
    private var signedIn = sessionState?.isSignedIn?.value ?: false
    private var navigatedToDetail = false

    override suspend fun reduce(intent: WaitingRoomIntent) {
        when (intent) {
            WaitingRoomIntent.Start -> startObserving(keepPlace = false)
            // TODO(FR-04 join #78): join again through the real API; the stub just restarts its script.
            WaitingRoomIntent.QueueAgain -> startObserving(keepPlace = false)
            WaitingRoomIntent.Retry -> startObserving(keepPlace = true)
            WaitingRoomIntent.Tick -> tick()
            WaitingRoomIntent.Back -> sendBackOnce()
            WaitingRoomIntent.ChooseTicketsNow -> navigateToDetail()
            WaitingRoomIntent.BrowseOtherEvents -> sendEffect(WaitingRoomEffect.NavigateToEvents)
            WaitingRoomIntent.LeaveQueue -> leave()
            WaitingRoomIntent.SignIn -> sendEffect(WaitingRoomEffect.OpenSignIn(eventId))
            WaitingRoomIntent.NotifyMe -> Unit
        }
    }

    /** Navigate to the Detail screen (manual path via "Choose tickets now"). */
    private fun navigateToDetail() {
        if (navigatedToDetail) return
        navigatedToDetail = true
        sendEffect(WaitingRoomEffect.NavigateToDetail(eventId))
    }

    /** Leaves the queue on the server, then goes back; a failed call keeps the place and shows the refresh error. */
    private suspend fun leave() {
        if (leaveQueue(eventId) is DomainResult.Success) {
            observeJob?.cancel()
            sendBackOnce()
        } else {
            sendEffect(WaitingRoomEffect.ShowRefreshError)
        }
    }

    /** A double tap on Back must not pop two destinations: only the first is delivered. */
    private fun sendBackOnce() {
        if (backSent) return
        backSent = true
        sendEffect(WaitingRoomEffect.NavigateBack)
    }

    @Suppress("ReturnCount") // Early returns for guard clauses and expired-turn transition.
    private fun tick() {
        val place = lastPlace ?: return
        if (!currentState.hasCountdown) return
        val elapsed = monotonicMillis() - receivedAtMillis
        val active = currentState.ui as? WaitingRoomUiState.Active
        val phase = active?.phase

        // If a guest is held on YourTurn and the countdown hits zero, transition to MissedTurn.
        if (phase is WaitingRoomPhase.YourTurn && phase.heldForSignIn) {
            val newPhase = place.toPhase(elapsed)
            if (newPhase is WaitingRoomPhase.YourTurn && newPhase.secondsLeft <= 0) {
                // Turn expired while waiting for sign-in -> missed.
                setState { copy(ui = active.copy(phase = WaitingRoomPhase.MissedTurn(place.rejoinEstimateMinutes))) }
                return
            }
        }
        val mapped = place.toUiState(elapsed)
        val updated =
            (mapped as? WaitingRoomUiState.Active)?.let { m ->
                val yourTurnPhase = m.phase as? WaitingRoomPhase.YourTurn
                val isHeld = !signedIn && yourTurnPhase != null
                m.copy(
                    isGuest = !signedIn,
                    phase =
                        if (isHeld && yourTurnPhase != null) {
                            yourTurnPhase.copy(heldForSignIn = true)
                        } else {
                            m.phase
                        },
                )
            } ?: mapped
        setState { copy(ui = updated) }
    }

    /**
     * Cancels any running observation and starts a new one. With [keepPlace] a good place
     * already on screen stays visible until the new snapshot replaces it; otherwise (and
     * after an error) the screen returns to Loading.
     */
    private fun startObserving(keepPlace: Boolean) {
        navigatedToDetail = false
        startSessionObserver()
        observeJob?.cancel()
        if (!keepPlace || currentState.ui !is WaitingRoomUiState.Active) {
            lastPlace = null
            setState { WaitingRoomState() }
        }
        observeJob =
            viewModelScope.launch {
                observeQueuePlace(eventId)
                    .onCompletion { cause ->
                        // Completing without ever emitting means the customer has no place.
                        // After an emission the last place stays (the stream just ended).
                        if (cause == null && currentState.ui == WaitingRoomUiState.Loading) {
                            setState { copy(ui = WaitingRoomUiState.Empty) }
                        }
                    }
                    .catch { cause -> onStreamFailed(cause) }
                    .collect { place -> onPlace(place) }
            }
    }

    private fun startSessionObserver() {
        if (sessionJob != null || sessionState == null) return
        sessionJob =
            viewModelScope.launch {
                sessionState.isSignedIn.collect { value ->
                    val wasSignedOut = !signedIn
                    signedIn = value
                    val active = currentState.ui as? WaitingRoomUiState.Active ?: return@collect

                    // If user just signed in while held at YourTurn, continue to Detail.
                    val wasHeld =
                        active.phase is WaitingRoomPhase.YourTurn && active.phase.heldForSignIn
                    if (value && wasSignedOut && wasHeld) {
                        navigateToDetail()
                        return@collect
                    }

                    // Otherwise update the guest/held state accordingly.
                    val newPhase =
                        if (active.phase is WaitingRoomPhase.YourTurn) {
                            active.phase.copy(heldForSignIn = !value)
                        } else {
                            active.phase
                        }
                    setState { copy(ui = active.copy(isGuest = !value, phase = newPhase)) }
                }
            }
    }

    private fun onPlace(place: QueuePlace) {
        lastPlace = place
        receivedAtMillis = monotonicMillis()
        val mapped = place.toUiState()

        // Determine the UI state with guest/held adjustments.
        val adjusted =
            (mapped as? WaitingRoomUiState.Active)?.let { active ->
                val yourTurnPhase = active.phase as? WaitingRoomPhase.YourTurn
                val isHeld = !signedIn && yourTurnPhase != null
                val newPhase =
                    if (isHeld && yourTurnPhase != null) {
                        yourTurnPhase.copy(heldForSignIn = true)
                    } else {
                        active.phase
                    }
                active.copy(isGuest = !signedIn, phase = newPhase)
            } ?: mapped

        setState { copy(ui = adjusted) }

        // Auto-advance: signed-in user at the turn goes straight to Detail.
        // The existing copy says "We'll move you in automatically" (waiting_turn_hint).
        if (signedIn && place.state == QueueState.Turn) {
            navigateToDetail()
        }
    }

    private fun onStreamFailed(cause: Throwable) {
        val signIn = cause is SignInRequiredException
        if (currentState.ui is WaitingRoomUiState.Active) {
            // Keep the last good place; the host offers a retry.
            sendEffect(if (signIn) WaitingRoomEffect.SignInRequired else WaitingRoomEffect.ShowRefreshError)
        } else {
            val kind = if (signIn) WaitingRoomErrorKind.SignInRequired else WaitingRoomErrorKind.Generic
            setState { copy(ui = WaitingRoomUiState.Error("", kind)) }
        }
    }
}

private fun defaultMonotonicMillis(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}
