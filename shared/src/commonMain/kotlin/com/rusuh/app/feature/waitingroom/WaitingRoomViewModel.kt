package com.rusuh.app.feature.waitingroom

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
            // TODO(AUTH-00): sign-in, TODO(FR-04-M4 #30): leave-queue and notify-me need the real API.
            WaitingRoomIntent.SignIn, WaitingRoomIntent.LeaveQueue, WaitingRoomIntent.NotifyMe -> Unit
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
                    .catch { onStreamFailed() }
                    .collect { place -> onPlace(place) }
            }
    }

    private fun onPlace(place: QueuePlace) {
        lastPlace = place
        receivedAtMillis = monotonicMillis()
        setState { copy(ui = place.toUiState()) }
    }

    private fun onStreamFailed() {
        if (currentState.ui is WaitingRoomUiState.Active) {
            // Keep the last good place; the host offers a retry.
            sendEffect(WaitingRoomEffect.ShowRefreshError)
        } else {
            setState { copy(ui = WaitingRoomUiState.Error("")) }
        }
    }
}

private fun defaultMonotonicMillis(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}
