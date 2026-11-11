package com.rusuh.app.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel for MVI architecture pattern.
 *
 * ## Why StateFlow for State vs Channel for Effects
 *
 * ### State uses [StateFlow]:
 * - **Hot stream**: Always has a current value, perfect for UI state that must always exist
 * - **Replay last value**: New collectors immediately receive the current state (e.g., after config change)
 * - **Conflation**: Only the latest state matters; intermediate states can be dropped under pressure
 * - **Multiple collectors**: All UI components observe the same shared state simultaneously
 *
 * ### Effects use [Channel]:
 * - **Cold/one-time delivery**: Each effect is consumed exactly once (no replay on resubscription)
 * - **No loss guarantee**: Buffered channel ensures effects aren't dropped even if UI is briefly unavailable
 * - **Fire-and-forget**: Navigation, snackbars, and toasts should not re-trigger on screen rotation
 * - **Single consumer**: Effects are typically handled by one collector (the Activity/Fragment)
 *
 * @param S The state type, must implement [MviState]
 * @param I The intent type, must implement [MviIntent]
 * @param E The effect type, must implement [MviEffect]
 * @param initialState The initial state of the ViewModel
 */
abstract class MviViewModel<S : MviState, I : MviIntent, E : MviEffect>(
    initialState: S,
) {
    protected val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * UI state exposed as [StateFlow].
     *
     * StateFlow is chosen because:
     * - It always holds the latest state value (hot stream)
     * - New subscribers immediately receive the current state (replay = 1)
     * - Multiple UI components can observe the same state simultaneously
     * - Configuration changes don't lose state since it's replayed to new collectors
     */
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    /**
     * One-time side effects exposed as a Flow backed by [Channel].
     *
     * Channel is chosen because:
     * - Effects must be consumed exactly once (no replay on resubscription)
     * - [Channel.BUFFERED] prevents effect loss if UI is temporarily unavailable
     * - Navigation, snackbars, dialogs should not re-trigger after config changes
     * - Single-consumer semantics match the typical effect handling pattern
     */
    private val _effect = Channel<E>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    protected val currentState: S get() = _state.value

    /** Dispatch an intent to be processed by the ViewModel. */
    fun dispatch(intent: I) {
        viewModelScope.launch {
            reduce(intent)
        }
    }

    /** Cancel all work started by this ViewModel; call when its owner leaves composition. */
    fun clear() {
        viewModelScope.cancel()
    }

    /** Process the intent and update state accordingly. */
    protected abstract suspend fun reduce(intent: I)

    /** Update the state using a reducer function. */
    protected fun setState(reducer: S.() -> S) {
        _state.value = currentState.reducer()
    }

    /** Send a one-time side effect. */
    protected fun sendEffect(effect: E) {
        viewModelScope.launch {
            _effect.send(effect)
        }
    }
}
