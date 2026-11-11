package com.rusuh.app.feature.waitingroom

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.SignInRequiredException
import com.rusuh.app.domain.usecase.LeaveQueueUseCase
import com.rusuh.app.feature.waitingroom.domain.model.QueuePlace
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
 * @param monotonicMillis monotonic milliseconds; injectable so tests can fake time.
 */
class WaitingRoomViewModel(
    private val eventId: String,
    private val observeQueuePlace: ObserveQueuePlaceUseCase,
    private val leaveQueue: LeaveQueueUseCase,
    private val monotonicMillis: () -> Long = defaultMonotonicMillis(),
) : MviViewModel<WaitingRoomState, WaitingRoomIntent, WaitingRoomEffect>(WaitingRoomState()) {
    private var observeJob: Job? = null
    private var lastPlace: QueuePlace? = null
    private var receivedAtMillis: Long = 0L
    private var backSent = false

    override suspend fun reduce(intent: WaitingRoomIntent) {
        when (intent) {
            WaitingRoomIntent.Start -> startObserving(keepPlace = false)
            // TODO(FR-04 join #78): join again through the real API; the stub just restarts its script.
            WaitingRoomIntent.QueueAgain -> startObserving(keepPlace = false)
            WaitingRoomIntent.Retry -> startObserving(keepPlace = true)
            WaitingRoomIntent.Tick -> tick()
            WaitingRoomIntent.Back -> sendBackOnce()
            WaitingRoomIntent.ChooseTicketsNow -> sendEffect(WaitingRoomEffect.NavigateToDetail(eventId))
            WaitingRoomIntent.BrowseOtherEvents -> sendEffect(WaitingRoomEffect.NavigateToEvents)
            WaitingRoomIntent.LeaveQueue -> leave()
            // TODO(AUTH-00 #77): sign-in; notify-me needs its own API.
            WaitingRoomIntent.SignIn, WaitingRoomIntent.NotifyMe -> Unit
        }
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

    private fun tick() {
        val place = lastPlace ?: return
        if (!currentState.hasCountdown) return
        setState { copy(ui = place.toUiState(monotonicMillis() - receivedAtMillis)) }
    }

    /**
     * Cancels any running observation and starts a new one. With [keepPlace] a good place
     * already on screen stays visible until the new snapshot replaces it; otherwise (and
     * after an error) the screen returns to Loading.
     */
    private fun startObserving(keepPlace: Boolean) {
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
        setState { copy(ui = place.toUiState()) }
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
