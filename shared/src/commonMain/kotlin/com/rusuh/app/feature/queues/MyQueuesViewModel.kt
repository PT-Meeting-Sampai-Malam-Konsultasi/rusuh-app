package com.rusuh.app.feature.queues

import com.rusuh.app.domain.usecase.GetMyQueuesUseCase
import com.rusuh.app.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * MVI ViewModel for the My Queues tab. Loads through [GetMyQueuesUseCase] only;
 * any failure becomes [MyQueuesPhase.Error] so the screen never hangs on Loading.
 */
class MyQueuesViewModel(
    private val getMyQueues: GetMyQueuesUseCase,
) : MviViewModel<MyQueuesState, MyQueuesIntent, MyQueuesEffect>(MyQueuesState()) {
    private var loadJob: Job? = null
    private var timerJob: Job? = null

    override suspend fun reduce(intent: MyQueuesIntent) {
        when (intent) {
            MyQueuesIntent.Load, MyQueuesIntent.Refresh, MyQueuesIntent.Retry -> startLoad()
            is MyQueuesIntent.OpenQueue -> sendEffect(MyQueuesEffect.NavigateToWaitingRoom(intent.eventId))
            is MyQueuesIntent.ContinueOrder -> {
                if (intent.order.bookingId == null) {
                    sendEffect(MyQueuesEffect.ContinueBooking(intent.order.eventId))
                } else {
                    sendEffect(MyQueuesEffect.ContinuePayment(intent.order.bookingId))
                }
            }
            MyQueuesIntent.ResumeExpired -> {
                sendEffect(MyQueuesEffect.ShowError(MyQueuesMessage.ResumeExpired))
                startLoad()
            }
            is MyQueuesIntent.SelectTab -> sendEffect(MyQueuesEffect.SwitchTab(intent.tab))
            MyQueuesIntent.BrowseEvents -> sendEffect(MyQueuesEffect.GoToEvents)
        }
    }

    /** Cancels any in-flight load first so an older response can never overwrite a newer one. */
    private fun startLoad() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun load() {
        // Keep showing the current rows while reloading; skeleton otherwise.
        val hadRows = currentState.phase == MyQueuesPhase.Ready
        if (!hadRows) setState { copy(phase = MyQueuesPhase.Loading) }
        try {
            val queues = getMyQueues().toUiItems()
            currentCoroutineContext().ensureActive()
            val orders = getMyQueues.getOrdersInProgress()
            currentCoroutineContext().ensureActive()
            val phase = if (queues.isEmpty() && orders.isEmpty()) MyQueuesPhase.Empty else MyQueuesPhase.Ready
            setState { copy(phase = phase, queues = queues, ordersInProgress = orders) }
            timerJob?.cancel()
            if (orders.isNotEmpty()) {
                timerJob =
                    viewModelScope.launch {
                        while (currentState.ordersInProgress.any { it.remainingMillis != null }) {
                            delay(1000)
                            val remaining =
                                currentState.ordersInProgress.mapNotNull { order ->
                                    val remaining = order.remainingMillis ?: return@mapNotNull order
                                    val next = order.copy(remainingMillis = remaining - 1000)
                                    next.takeIf { (it.remainingMillis ?: 0) > 0 }
                                }
                            setState {
                                copy(
                                    ordersInProgress = remaining,
                                    phase = if (queues.isEmpty() && remaining.isEmpty()) MyQueuesPhase.Empty else phase,
                                )
                            }
                        }
                    }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("SwallowedException") failure: Exception,
        ) {
            currentCoroutineContext().ensureActive()
            if (hadRows) {
                // Keep the rows on screen; surface a transient message instead.
                sendEffect(MyQueuesEffect.ShowError(MyQueuesMessage.RefreshFailed))
            } else {
                setState { copy(phase = MyQueuesPhase.Error, queues = emptyList()) }
            }
        }
    }
}
