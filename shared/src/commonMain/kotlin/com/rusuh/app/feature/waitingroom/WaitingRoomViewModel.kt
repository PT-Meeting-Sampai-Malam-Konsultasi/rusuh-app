package com.rusuh.app.feature.waitingroom

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.SignInRequiredException
import com.rusuh.app.domain.repository.QueueClaimOutcome
import com.rusuh.app.domain.usecase.ClaimQueueUseCase
import com.rusuh.app.domain.usecase.LeaveQueueUseCase
import com.rusuh.app.feature.waitingroom.domain.model.QueuePlace
import com.rusuh.app.feature.waitingroom.domain.model.QueueState
import com.rusuh.app.feature.waitingroom.domain.usecase.ObserveQueuePlaceUseCase
import com.rusuh.app.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

const val AUTO_CLAIM_LEAD_MILLIS = 5_000L

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
 * ## Login gate (AUTH-00-M8 #154)
 *
 * The queue entry gate ensures users are signed in before reaching this screen. The only
 * sign-in path from here is the defensive re-auth: if the session is lost mid-queue
 * (401/SignInRequiredException during polling), [WaitingRoomEffect.ReSignIn] opens sign-in;
 * after re-signing in the user resumes the same place (join is idempotent, the place
 * belongs to the device).
 *
 * @param monotonicMillis monotonic milliseconds; injectable so tests can fake time.
 * @param monotonicMillis monotonic milliseconds; injectable so tests can fake time.
 */
class WaitingRoomViewModel(
    private val eventId: String,
    private val observeQueuePlace: ObserveQueuePlaceUseCase,
    private val leaveQueue: LeaveQueueUseCase,
    private val monotonicMillis: () -> Long = defaultMonotonicMillis(),
    private val claimQueue: ClaimQueueUseCase? = null,
) : MviViewModel<WaitingRoomState, WaitingRoomIntent, WaitingRoomEffect>(WaitingRoomState()) {
    private var observeJob: Job? = null
    private var autoClaimJob: Job? = null
    private var lastPlace: QueuePlace? = null
    private var receivedAtMillis: Long = 0L
    private var backSent = false
    private var navigatedToDetail = false
    private var screenActive = false
    private var claimInFlight = false

    override suspend fun reduce(intent: WaitingRoomIntent) {
        when (intent) {
            WaitingRoomIntent.Start -> startObserving(keepPlace = false)
            // TODO(FR-04 join #78): join again through the real API; the stub just restarts its script.
            WaitingRoomIntent.QueueAgain -> startObserving(keepPlace = false)
            WaitingRoomIntent.Retry -> startObserving(keepPlace = true)
            WaitingRoomIntent.Tick -> tick()
            WaitingRoomIntent.ScreenActive -> {
                screenActive = true
                scheduleAutoClaim()
            }
            WaitingRoomIntent.ScreenInactive -> {
                screenActive = false
                autoClaimJob?.cancel()
                autoClaimJob = null
            }
            WaitingRoomIntent.Back -> sendBackOnce()
            WaitingRoomIntent.ChooseTicketsNow -> claim()
            WaitingRoomIntent.BrowseOtherEvents -> sendEffect(WaitingRoomEffect.NavigateToEvents)
            WaitingRoomIntent.LeaveQueue -> leave()
            WaitingRoomIntent.NotifyMe -> Unit
        }
    }

    /** Navigate to the Detail screen after admission. */
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

    @Suppress("ReturnCount")
    private fun tick() {
        val place = lastPlace ?: return
        if (!currentState.hasCountdown) return
        val elapsed = monotonicMillis() - receivedAtMillis
        val mapped = place.toUiState(elapsed)
        val turn = (mapped as? WaitingRoomUiState.Active)?.phase as? WaitingRoomPhase.YourTurn
        if (turn?.secondsLeft == 0 && !claimInFlight) {
            setState { copy(ui = place.copy(state = QueueState.Missed).toUiState(elapsed)) }
            return
        }
        val claiming =
            ((currentState.ui as? WaitingRoomUiState.Active)?.phase as? WaitingRoomPhase.YourTurn)?.claiming == true
        setState {
            copy(
                ui =
                    if (claiming && turn != null) {
                        (mapped as WaitingRoomUiState.Active).copy(phase = turn.copy(claiming = true))
                    } else {
                        mapped
                    },
            )
        }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount", "TooGenericExceptionCaught", "InstanceOfCheckForException")
    private suspend fun claim() {
        val place = lastPlace ?: return
        val expiresAt = place.turnExpiresAtEpochMillis ?: return
        val turn = (currentState.ui as? WaitingRoomUiState.Active)?.phase as? WaitingRoomPhase.YourTurn ?: return
        if (claimInFlight || turn.claiming || turn.secondsLeft <= 0) return
        if (!screenActive) return
        claimInFlight = true
        val active = place.toUiState() as? WaitingRoomUiState.Active ?: return
        setState { copy(ui = active.copy(phase = turn.copy(claiming = true))) }
        try {
            val serverNow = place.serverNowEpochMillis + (monotonicMillis() - receivedAtMillis).coerceAtLeast(0L)
            when (claimQueue?.invoke(eventId, expiresAt, serverNow) ?: QueueClaimOutcome.Admitted) {
                QueueClaimOutcome.Admitted -> navigateToDetail()
                QueueClaimOutcome.BackToWaiting -> {
                    setState { copy(ui = place.copy(state = QueueState.Open).toUiState()) }
                    sendEffect(WaitingRoomEffect.ShowNoStock)
                }
                QueueClaimOutcome.Missed -> setState { copy(ui = place.copy(state = QueueState.Missed).toUiState()) }
                QueueClaimOutcome.Closed -> setState { copy(ui = place.copy(state = QueueState.Closed).toUiState()) }
            }
        } catch (failure: Throwable) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            if (failure is SignInRequiredException) {
                sendEffect(WaitingRoomEffect.ReSignIn(eventId))
            } else {
                sendEffect(WaitingRoomEffect.ShowRefreshError)
            }
            setState { copy(ui = place.toUiState((monotonicMillis() - receivedAtMillis).coerceAtLeast(0L))) }
        } finally {
            claimInFlight = false
        }
    }

    /**
     * Cancels any running observation and starts a new one. With [keepPlace] a good place
     * already on screen stays visible until the new snapshot replaces it; otherwise (and
     * after an error) the screen returns to Loading.
     */
    private fun startObserving(keepPlace: Boolean) {
        navigatedToDetail = false
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

    private fun onPlace(place: QueuePlace) {
        lastPlace = place
        receivedAtMillis = monotonicMillis()
        val mapped = place.toUiState()

        setState { copy(ui = mapped) }
        if (place.state == QueueState.Turn && screenActive) scheduleAutoClaim()
    }

    @Suppress("ReturnCount")
    private fun scheduleAutoClaim() {
        autoClaimJob?.cancel()
        val place = lastPlace ?: return
        if (!screenActive || place.state != QueueState.Turn) return
        val expiresAt = place.turnExpiresAtEpochMillis ?: return
        val serverNow = place.serverNowEpochMillis + (monotonicMillis() - receivedAtMillis).coerceAtLeast(0L)
        val waitMillis = (expiresAt - serverNow - AUTO_CLAIM_LEAD_MILLIS).coerceAtLeast(0L)
        autoClaimJob =
            viewModelScope.launch {
                delay(waitMillis)
                if (screenActive) claim()
            }
    }

    private fun onStreamFailed(cause: Throwable) {
        val signIn = cause is SignInRequiredException
        if (currentState.ui is WaitingRoomUiState.Active) {
            // Keep the last good place; open re-sign-in or offer a retry.
            if (signIn) {
                sendEffect(WaitingRoomEffect.ReSignIn(eventId))
            } else {
                sendEffect(WaitingRoomEffect.ShowRefreshError)
            }
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
