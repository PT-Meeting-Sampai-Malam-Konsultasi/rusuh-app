package com.rusuh.app.data.network.session

import com.rusuh.app.data.storage.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single-flight session refresh. Concurrent 401s queue on one lock; the first one calls `POST /auth/refresh`, the
 * others find the new access token already in the [TokenStore] and reuse it, so the server sees exactly one refresh
 * (a second use of a rotated refresh token would revoke the whole token family).
 *
 * [refresher] is lazy because the default [ApiTokenRefresher] needs an ApiClient that itself needs this coordinator.
 */
class SessionRefreshCoordinator(
    private val tokenStore: TokenStore,
    private val refresher: Lazy<TokenRefresher>,
    private val events: SessionEvents,
) {
    private val lock = Mutex()

    /**
     * Returns the access token to retry with, or null when the call should keep its 401. [usedAccessToken] is the
     * Bearer the failed request carried (null when it carried none). A rejected refresh, or no refresh token while a
     * session existed, clears the session and emits [SessionEvent.SessionExpired]. A refresh that merely could not
     * complete (offline, 5xx) keeps the session and returns null.
     */
    suspend fun refreshAfterUnauthorized(usedAccessToken: String?): String? =
        lock.withLock {
            val current = tokenStore.accessToken
            when {
                // A concurrent 401 already refreshed while this one waited for the lock.
                current != null && current != usedAccessToken -> current
                // Signed out meanwhile (expired or logout): nothing to refresh, and the event was already sent.
                current == null && usedAccessToken != null -> null
                // The refresh must finish once started: losing a rotated refresh token would end the session.
                else -> withContext(NonCancellable) { refreshNow(hadSession = usedAccessToken != null) }
            }
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun refreshNow(hadSession: Boolean): String? {
        val refreshToken = tokenStore.getRefreshToken()
        val outcome =
            if (refreshToken == null) {
                RefreshOutcome.Rejected.takeIf { hadSession }
            } else {
                try {
                    refresher.value.refresh(refreshToken)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    RefreshOutcome.Unavailable
                }
            }
        return when (outcome) {
            is RefreshOutcome.Refreshed -> store(outcome, usedRefreshToken = refreshToken)
            RefreshOutcome.Rejected -> {
                expire()
                null
            }
            RefreshOutcome.Unavailable, null -> null
        }
    }

    private suspend fun store(
        refreshed: RefreshOutcome.Refreshed,
        usedRefreshToken: String?,
    ): String? {
        // A logout while the refresh was in flight removed the refresh token: do not bring the session back.
        // (Best effort: a logout between this check and the writes below is not detectable through TokenStore.)
        if (usedRefreshToken != null && tokenStore.getRefreshToken() != usedRefreshToken) return null
        refreshed.refreshToken?.let { tokenStore.setRefreshToken(it) }
        tokenStore.accessToken = refreshed.accessToken
        return refreshed.accessToken
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun expire() {
        try {
            tokenStore.clearSession()
        } catch (failure: Exception) {
            // Cleared as far as possible (the access token goes first); the user is signed out either way.
        } finally {
            events.emit(SessionEvent.SessionExpired)
        }
    }
}
