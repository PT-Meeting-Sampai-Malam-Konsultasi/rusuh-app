package com.rusuh.app.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel for MVI architecture pattern.
 *
 * @param S The state type, must implement [MviState]
 * @param I The intent type, must implement [MviIntent]
 * @param E The effect type, must implement [MviEffect]
 * @param initialState The initial state of the ViewModel
 */
abstract class MviViewModel<S : MviState, I : MviIntent, E : MviEffect>(
    initialState: S,
) {
    protected val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effect = Channel<E>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    protected val currentState: S get() = _state.value

    /** Dispatch an intent to be processed by the ViewModel. */
    fun dispatch(intent: I) {
        viewModelScope.launch {
            reduce(intent)
        }
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
