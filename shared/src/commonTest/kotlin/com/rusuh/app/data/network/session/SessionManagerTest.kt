package com.rusuh.app.data.network.session

import com.rusuh.app.data.storage.FakeSecureStore
import com.rusuh.app.data.storage.SecureTokenStore
import com.rusuh.app.data.storage.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Not an Exception: the restore must survive any Throwable. */
private class StorageDied : Throwable("keystore died")

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private class Fixture(
        scope: CoroutineScope,
        refreshToken: String? = null,
        presetExpired: Boolean = false,
    ) {
        val secure = FakeSecureStore()
        val tokens = SecureTokenStore(secure)
        val events = SessionEvents()
        val manager: SessionManager

        init {
            refreshToken?.let { secure.entries["auth_refresh_token"] = it }
            if (presetExpired) events.emit(SessionEvent.SessionExpired)
            manager = SessionManager(tokens, events, scope)
        }
    }

    private fun TestScope.fixture(
        refreshToken: String? = null,
        presetExpired: Boolean = false,
    ) = Fixture(backgroundScope, refreshToken, presetExpired)

    @Test
    fun isFalseAndNotRestoredUntilTheStorageReadRuns() =
        runTest {
            val f = fixture(refreshToken = "refresh-1")

            assertFalse(f.manager.isSignedIn.value)
            assertFalse(f.manager.isRestored.value)
            runCurrent()

            assertTrue(f.manager.isSignedIn.value)
            assertTrue(f.manager.isRestored.value)
        }

    @Test
    fun coldStartWithAStoredRefreshTokenIsSignedInWithoutAnAccessToken() =
        runTest {
            val f = fixture(refreshToken = "refresh-1")

            assertTrue(f.manager.awaitSignedIn())
            assertEquals(null, f.tokens.accessToken, "the access token is memory only; the first 401 refreshes it")
        }

    @Test
    fun coldStartWithoutARefreshTokenIsSignedOut() =
        runTest {
            val f = fixture()

            assertFalse(f.manager.awaitSignedIn())
            assertTrue(f.manager.isRestored.value)
        }

    @Test
    fun coldStartAfterAnExpiryIsSignedOutEvenIfATokenIsStillThere() =
        runTest {
            val f = fixture(refreshToken = "refresh-1", presetExpired = true)

            assertFalse(f.manager.awaitSignedIn())
        }

    @Test
    fun aLoginBeforeTheRestoreRunsWins() =
        runTest {
            val f = fixture()

            f.manager.startSession("access-1", "refresh-1")
            runCurrent()

            assertTrue(f.manager.isSignedIn.value)
            assertEquals("refresh-1", f.tokens.getRefreshToken())
        }

    @Test
    fun aLogoutBeforeTheRestoreRunsWins() =
        runTest {
            val f = fixture(refreshToken = "refresh-1")

            f.manager.endSession()
            runCurrent()

            assertFalse(f.manager.isSignedIn.value)
            assertTrue(f.manager.isRestored.value)
        }

    @Test
    fun startSessionStoresTheTokensClearsExpiredAndSignsIn() =
        runTest {
            val f = fixture()
            f.events.emit(SessionEvent.SessionExpired)
            runCurrent()

            f.manager.startSession("access-1", "refresh-1")

            assertTrue(f.manager.isSignedIn.value)
            assertEquals("access-1", f.tokens.accessToken)
            assertEquals("refresh-1", f.secure.entries["auth_refresh_token"])
            assertFalse(f.events.expired.value)
        }

    @Test
    fun endSessionSignsOutAndDropsTheTokens() =
        runTest {
            val f = fixture()
            f.manager.startSession("access-1", "refresh-1")

            f.manager.endSession()

            assertFalse(f.manager.isSignedIn.value)
            assertEquals(null, f.tokens.accessToken)
            assertEquals(null, f.tokens.getRefreshToken())
        }

    @Test
    fun endSessionSignsOutEvenWhenTheStorageThrowsAndThenRethrows() =
        runTest {
            val f = fixture()
            f.manager.startSession("access-1", "refresh-1")
            f.secure.failingRemovals += "auth_refresh_token"

            assertFailsWith<IllegalStateException> { f.manager.endSession() }

            assertFalse(f.manager.isSignedIn.value)
        }

    @Test
    fun sessionExpiredFlipsTheFlagAndALaterLoginFlipsItBack() =
        runTest {
            val f = fixture()
            f.manager.startSession("access-1", "refresh-1")
            runCurrent()

            f.events.emit(SessionEvent.SessionExpired)
            runCurrent()
            assertFalse(f.manager.isSignedIn.value)

            f.manager.startSession("access-2", "refresh-2")
            runCurrent()
            assertTrue(f.manager.isSignedIn.value)
        }

    @Test
    fun anExpiryThatWasOverruledByALoginBeforeItWasHandledDoesNotSignOut() =
        runTest {
            val f = fixture()
            f.manager.startSession("access-1", "refresh-1")
            runCurrent()

            // Expired and the login both happen before the listener gets to run.
            f.events.emit(SessionEvent.SessionExpired)
            f.manager.startSession("access-2", "refresh-2")
            runCurrent()

            assertTrue(f.manager.isSignedIn.value)
        }

    @Test
    fun startSessionWithoutARefreshTokenRemovesTheStoredOne() =
        runTest {
            val f = fixture(refreshToken = "refresh-old")

            f.manager.startSession("access-1", null)

            assertEquals(null, f.tokens.getRefreshToken())
            assertTrue(f.manager.isSignedIn.value)
        }

    @Test
    fun awaitSignedInGivesUpWhenTheStorageReadHangs() =
        runTest {
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val tokens =
                object : TokenStore by SecureTokenStore(FakeSecureStore()) {
                    override suspend fun getRefreshToken(): String? {
                        gate.await()
                        return "refresh-1"
                    }
                }
            val manager = SessionManager(tokens, SessionEvents(), backgroundScope, restoreTimeout = 2.seconds)

            assertFalse(manager.awaitSignedIn(), "documented fallback: signed out until the read finishes")
            assertFalse(manager.isRestored.value)

            gate.complete(Unit)
            assertTrue(manager.awaitSignedIn())
        }

    @Test
    fun aStorageReadThatThrowsAnythingIsSignedOutNotAHang() =
        runTest {
            val tokens =
                object : TokenStore by SecureTokenStore(FakeSecureStore()) {
                    override suspend fun getRefreshToken(): String? = throw StorageDied()
                }
            val manager = SessionManager(tokens, SessionEvents(), backgroundScope)

            assertFalse(manager.awaitSignedIn())
            assertTrue(manager.isRestored.value)
        }

    @Test
    fun closeStopsListeningToExpiry() =
        runTest {
            val f = fixture()
            f.manager.startSession("access-1", "refresh-1")
            runCurrent()

            f.manager.close()
            f.events.emit(SessionEvent.SessionExpired)
            runCurrent()

            assertTrue(f.manager.isSignedIn.value)
        }
}
