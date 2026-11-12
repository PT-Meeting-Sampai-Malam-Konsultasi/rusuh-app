package com.rusuh.app.data.network.session

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SessionEvent {
    /** The refresh token is gone or rejected: tokens were cleared and the user must sign in again. */
    data object SessionExpired : SessionEvent
}

/**
 * Process-wide session signals. The UI shell collects [events] once and routes to sign-in on
 * [SessionEvent.SessionExpired].
 *
 * [events] is not replayed, so an event with no collector is lost; [expired] is the sticky counterpart: it turns
 * true with the event and stays true until [reset], so a shell that starts collecting late (or a screen that
 * resumes) still learns the session is over. [reset] is called when a login stores tokens
 * (`SessionManager.startSession`) and automatically on the first request that carries an access token again.
 */
class SessionEvents {
    private val flow =
        MutableSharedFlow<SessionEvent>(extraBufferCapacity = BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val expiredState = MutableStateFlow(false)

    val events: SharedFlow<SessionEvent> = flow.asSharedFlow()

    /** True from a [SessionEvent.SessionExpired] until the next login. */
    val expired: StateFlow<Boolean> = expiredState.asStateFlow()

    fun emit(event: SessionEvent) {
        if (event == SessionEvent.SessionExpired) expiredState.value = true
        flow.tryEmit(event)
    }

    /** Clears the sticky [expired] state: the user signed in again. */
    fun reset() {
        expiredState.value = false
    }

    private companion object {
        const val BUFFER = 8
    }
}
