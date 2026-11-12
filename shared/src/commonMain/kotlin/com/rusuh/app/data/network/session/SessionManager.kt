package com.rusuh.app.data.network.session

import com.rusuh.app.data.storage.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one answer to "is there a signed-in account", derived from the token state.
 *
 * - [isSignedIn] turns true in [startSession] (login), false in [endSession] (logout) and when the refresh layer
 *   emits [SessionEvent.SessionExpired] (rejected refresh token).
 * - Cold start: the access token is memory-only, so the initial value is restored asynchronously from whether a
 *   refresh token is in secure storage. Until that read finishes [isSignedIn] is false and [isRestored] is false;
 *   a screen that must not flash "signed out" waits with [awaitSignedIn]. A login or logout that happens first wins
 *   over the restore.
 * - All state changes run under one lock, so a restore, a login and a logout never interleave.
 *
 * @param scope runs the restore and the expiry listener; the default is the app-lifetime scope, tests pass their own.
 */
class SessionManager(
    private val tokenStore: TokenStore,
    private val events: SessionEvents,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val lock = Mutex()
    private val signedIn = MutableStateFlow(false)
    private val restored = MutableStateFlow(false)
    private val jobs: List<Job>

    val isSignedIn: StateFlow<Boolean> = signedIn.asStateFlow()

    /** False until the cold-start read of the stored refresh token finished (or a login/logout decided first). */
    val isRestored: StateFlow<Boolean> = restored.asStateFlow()

    init {
        jobs =
            listOf(
                scope.launch { restore() },
                scope.launch {
                    events.expired.collect { expired -> if (expired) markSignedOut() }
                },
            )
    }

    /** Waits for the cold-start restore, then returns [isSignedIn]. */
    suspend fun awaitSignedIn(): Boolean {
        restored.first { it }
        return signedIn.value
    }

    /**
     * Stores the tokens of a successful login the same way the refresh layer does (the refresh token through the
     * [TokenStore], the access token in memory), clears the sticky expired state and turns [isSignedIn] true. A
     * storage failure propagates and leaves the state untouched. [refreshToken] null keeps the stored one.
     */
    suspend fun startSession(
        accessToken: String,
        refreshToken: String?,
    ) = lock.withLock {
        refreshToken?.let { tokenStore.setRefreshToken(it) }
        tokenStore.accessToken = accessToken
        events.reset()
        signedIn.value = true
        restored.value = true
    }

    /**
     * Drops the session tokens and turns [isSignedIn] false. The flag flips even when clearing storage throws (the
     * exception then propagates): the user asked to be signed out, so the app must not keep acting signed in.
     */
    suspend fun endSession() =
        lock.withLock {
            try {
                tokenStore.clearSession()
            } finally {
                signedIn.value = false
                restored.value = true
            }
        }

    /** Stops the listeners; only for the scope owner (Koin close). */
    fun close() {
        jobs.forEach { it.cancel() }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun restore() {
        val hasRefreshToken =
            try {
                tokenStore.getRefreshToken() != null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                false // unreadable storage: treat as signed out, the user can sign in again
            }
        lock.withLock {
            // A login, logout or expiry that already happened is newer than this read.
            if (!restored.value) {
                signedIn.value = hasRefreshToken && !events.expired.value
                restored.value = true
            }
        }
    }

    private suspend fun markSignedOut() =
        lock.withLock {
            // A login that ran while this waited for the lock reset the expired state: it wins.
            if (events.expired.value) {
                signedIn.value = false
                restored.value = true
            }
        }
}
