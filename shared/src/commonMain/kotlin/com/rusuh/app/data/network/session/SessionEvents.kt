package com.rusuh.app.data.network.session

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface SessionEvent {
    /** The refresh token is gone or rejected: tokens were cleared and the user must sign in again. */
    data object SessionExpired : SessionEvent
}

/**
 * Process-wide session signals. The UI shell collects [events] once and routes to sign-in on
 * [SessionEvent.SessionExpired]. Nothing is replayed: an event with no collector is dropped, which is fine because
 * the tokens are already cleared and the next request starts signed out.
 */
class SessionEvents {
    private val flow =
        MutableSharedFlow<SessionEvent>(extraBufferCapacity = BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val events: SharedFlow<SessionEvent> = flow.asSharedFlow()

    fun emit(event: SessionEvent) {
        flow.tryEmit(event)
    }

    private companion object {
        const val BUFFER = 8
    }
}
