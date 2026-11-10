package com.rusuh.app.feature.waitingroom

import com.rusuh.app.feature.waitingroom.domain.usecase.ObserveQueuePlaceUseCase
import com.rusuh.app.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the Waiting Room of one event.
 *
 * Observes the queue place through [ObserveQueuePlaceUseCase] and reduces it into
 * [WaitingRoomState]; user [WaitingRoomIntent]s become [WaitingRoomEffect]s.
 */
class WaitingRoomViewModel(
    private val eventId: String,
    private val observeQueuePlace: ObserveQueuePlaceUseCase,
) : MviViewModel<WaitingRoomState, WaitingRoomIntent, WaitingRoomEffect>(WaitingRoomState()) {
    private var observeJob: Job? = null

    override suspend fun reduce(intent: WaitingRoomIntent) {
        when (intent) {
            WaitingRoomIntent.Start -> startObserving()
            // TODO(FR-04 join #78): join again through the real API; the stub just restarts its script.
            WaitingRoomIntent.QueueAgain -> startObserving()
            WaitingRoomIntent.Back -> sendEffect(WaitingRoomEffect.NavigateBack)
            WaitingRoomIntent.ChooseTicketsNow -> sendEffect(WaitingRoomEffect.NavigateToDetail(eventId))
            WaitingRoomIntent.BrowseOtherEvents -> sendEffect(WaitingRoomEffect.NavigateToEvents)
            // TODO(AUTH-00): sign-in, TODO(FR-04-M4 #30): leave-queue and notify-me need the real API.
            WaitingRoomIntent.SignIn, WaitingRoomIntent.LeaveQueue, WaitingRoomIntent.NotifyMe -> Unit
        }
    }

    private fun startObserving() {
        observeJob?.cancel()
        setState { WaitingRoomState() }
        observeJob =
            viewModelScope.launch {
                observeQueuePlace(eventId)
                    .onCompletion { cause ->
                        // Completing without ever emitting means the customer has no place.
                        if (cause == null && currentState.ui == WaitingRoomUiState.Loading) {
                            setState { copy(ui = WaitingRoomUiState.Empty) }
                        }
                    }
                    .catch { setState { copy(ui = WaitingRoomUiState.Empty, loadFailed = true) } }
                    .collect { place -> setState { copy(ui = place.toUiState(), loadFailed = false) } }
            }
    }
}
