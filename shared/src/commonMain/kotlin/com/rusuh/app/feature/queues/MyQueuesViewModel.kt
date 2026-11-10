package com.rusuh.app.feature.queues

import com.rusuh.app.domain.usecase.GetMyQueuesUseCase
import com.rusuh.app.mvi.MviViewModel
import kotlin.coroutines.cancellation.CancellationException

/**
 * MVI ViewModel for the My Queues tab. Loads through [GetMyQueuesUseCase] only;
 * any failure becomes [MyQueuesPhase.Error] so the screen never hangs on Loading.
 */
class MyQueuesViewModel(
    private val getMyQueues: GetMyQueuesUseCase,
) : MviViewModel<MyQueuesState, MyQueuesIntent, MyQueuesEffect>(MyQueuesState()) {
    override suspend fun reduce(intent: MyQueuesIntent) {
        when (intent) {
            MyQueuesIntent.Load, MyQueuesIntent.Refresh, MyQueuesIntent.Retry -> load()
            is MyQueuesIntent.OpenQueue -> sendEffect(MyQueuesEffect.NavigateToWaitingRoom(intent.eventId))
            is MyQueuesIntent.SelectTab -> sendEffect(MyQueuesEffect.SwitchTab(intent.tab))
            MyQueuesIntent.BrowseEvents -> sendEffect(MyQueuesEffect.GoToEvents)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun load() {
        // Keep showing the current rows while refreshing; skeleton otherwise.
        if (currentState.phase != MyQueuesPhase.Ready) {
            setState { copy(phase = MyQueuesPhase.Loading) }
        }
        try {
            val queues = getMyQueues().toUiItems()
            val phase = if (queues.isEmpty()) MyQueuesPhase.Empty else MyQueuesPhase.Ready
            setState { copy(phase = phase, queues = queues) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("SwallowedException") failure: Exception,
        ) {
            setState { copy(phase = MyQueuesPhase.Error, queues = emptyList()) }
        }
    }
}
