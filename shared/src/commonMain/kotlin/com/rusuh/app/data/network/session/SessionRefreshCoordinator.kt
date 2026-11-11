package com.rusuh.app.data.network.session

import com.rusuh.app.data.storage.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Single-flight session refresh. All 401s that arrive while a refresh runs await the SAME in-flight refresh and get
 * the same result, success or failure, so the server sees exactly one refresh (a second use of a rotated refresh
 * token would revoke the whole token family). The refresh runs in the coordinator's own [scope], so a caller that is
 * cancelled while waiting does not cancel it for the others, and a started refresh always finishes (losing a rotated
 * refresh token would end the session).
 *
 * After a refresh that could not complete (offline, 5xx) further 401s get no new attempt for [unavailableCooldown],
 * so a burst of requests does not hammer a failing endpoint. [timeSource] is injectable for tests.
 *
 * [refresher] is lazy because the default [ApiTokenRefresher] needs an ApiClient that itself needs this coordinator.
 */
class SessionRefreshCoordinator(
    private val tokenStore: TokenStore,
    private val refresher: Lazy<TokenRefresher>,
    private val events: SessionEvents,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val unavailableCooldown: Duration = DEFAULT_COOLDOWN,
) {
    private val lock = Mutex()
    private var inFlight: Deferred<String?>? = null
    private var unavailableAt: TimeMark? = null

    /** Called when a request carries an access token: a login happened, so the sticky expired state is over. */
    fun notifySignedIn() {
        if (events.expired.value) events.reset()
    }

    /**
     * Returns the access token to retry with, or null when the call should keep its 401. [usedAccessToken] is the
     * Bearer the failed request carried (null when it carried none). A rejected refresh, or no refresh token while a
     * session existed, clears the session and emits [SessionEvent.SessionExpired]. A refresh that merely could not
     * complete (offline, 5xx) keeps the session and returns null.
     */
    suspend fun refreshAfterUnauthorized(usedAccessToken: String?): String? =
        when (val plan = lock.withLock { plan(usedAccessToken) }) {
            is Plan.Done -> plan.token
            is Plan.Wait -> plan.refresh.await()
        }

    private sealed interface Plan {
        class Done(val token: String?) : Plan

        class Wait(val refresh: Deferred<String?>) : Plan
    }

    /** Decides under [lock]: answer at once, or join (starting if needed) the one in-flight refresh. */
    private fun plan(usedAccessToken: String?): Plan {
        val current = tokenStore.accessToken
        return when {
            // A refresh already finished while this 401 was on its way back.
            current != null && current != usedAccessToken -> Plan.Done(current)
            // Signed out meanwhile (expired or logout): nothing to refresh, and the event was already sent.
            current == null && usedAccessToken != null -> Plan.Done(null)
            inFlight == null && inCooldown() -> Plan.Done(null)
            else ->
                Plan.Wait(
                    inFlight ?: scope
                        .async { runRefresh(hadSession = usedAccessToken != null) }
                        .also { inFlight = it },
                )
        }
    }

    private fun inCooldown(): Boolean = unavailableAt?.let { it.elapsedNow() < unavailableCooldown } == true

    private suspend fun runRefresh(hadSession: Boolean): String? =
        withContext(NonCancellable) {
            try {
                refreshNow(hadSession)
            } finally {
                lock.withLock { inFlight = null }
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
        unavailableAt = if (outcome == RefreshOutcome.Unavailable) timeSource.markNow() else null
        return when (outcome) {
            is RefreshOutcome.Refreshed -> store(outcome, usedRefreshToken = refreshToken)
            RefreshOutcome.Rejected -> {
                // Never wipe a newer login: only expire when the rejected token is still the stored one.
                if (tokenStore.getRefreshToken() == refreshToken) expire()
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

    private companion object {
        val DEFAULT_COOLDOWN = 5.seconds
    }
}
