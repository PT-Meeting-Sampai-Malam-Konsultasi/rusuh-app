package com.rusuh.app.data.network.session

/** Calls `POST /auth/refresh`. The real `AuthRepository.refresh` (EPIC-01-M6 #67) can replace [ApiTokenRefresher]. */
fun interface TokenRefresher {
    suspend fun refresh(refreshToken: String): RefreshOutcome
}

sealed interface RefreshOutcome {
    /** New tokens. [refreshToken] is the rotated one, null when the server did not send a new one. */
    class Refreshed(
        val accessToken: String,
        val refreshToken: String?,
    ) : RefreshOutcome {
        override fun toString(): String = "Refreshed(tokens redacted)"
    }

    /** The server refused the refresh token (400 or 401): the session is over. */
    data object Rejected : RefreshOutcome

    /** Offline, timeout, 5xx or an unreadable answer: the session may still be valid, so it is kept. */
    data object Unavailable : RefreshOutcome
}
