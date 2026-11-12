package com.rusuh.app.data.network.session

import com.rusuh.app.domain.session.SessionObserver
import com.rusuh.app.domain.session.SessionPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** [SessionObserver] over the [SessionManager] and the [SessionEvents]. */
class SessionManagerObserver(
    private val session: SessionManager,
    events: SessionEvents,
) : SessionObserver {
    override val currentPhase: SessionPhase
        get() =
            when {
                !session.isRestored.value -> SessionPhase.Restoring
                session.isSignedIn.value -> SessionPhase.SignedIn
                else -> SessionPhase.SignedOut
            }

    override val phase: Flow<SessionPhase> =
        combine(session.isRestored, session.isSignedIn) { restored, signedIn ->
            when {
                !restored -> SessionPhase.Restoring
                signedIn -> SessionPhase.SignedIn
                else -> SessionPhase.SignedOut
            }
        }

    override val expired: Flow<Unit> =
        events.events.filter { it == SessionEvent.SessionExpired }.map { }

    override suspend fun awaitSignedIn(): Boolean = session.awaitSignedIn()
}
