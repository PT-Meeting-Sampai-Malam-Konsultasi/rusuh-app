package com.rusuh.app.domain.session

import kotlinx.coroutines.flow.Flow

/** What the app knows about the account session right now. */
enum class SessionPhase {
    /** Cold start: the stored session has not been read yet. Show a neutral state, never the login. */
    Restoring,
    SignedOut,
    SignedIn,
}

/**
 * Read-only view of the session for the UI layer (the implementation is `SessionManagerObserver` over the
 * `SessionManager`). Kept as an interface so the domain layer and the tests do not depend on the data layer.
 */
interface SessionObserver {
    /** The current phase, replaying the latest value to a new collector. */
    val phase: Flow<SessionPhase>

    /** One item per `SessionEvent.SessionExpired` (the refresh token was rejected). Not replayed. */
    val expired: Flow<Unit>

    /** Waits for the cold-start restore (bounded) and answers whether an account is signed in. */
    suspend fun awaitSignedIn(): Boolean
}
