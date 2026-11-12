package com.rusuh.app.data.network.session

import com.rusuh.app.data.storage.FakeSecureStore
import com.rusuh.app.data.storage.SecureTokenStore
import com.rusuh.app.domain.session.SessionPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerObserverTest {
    @Test
    fun theColdStartIsRestoringUntilTheStoredSessionIsRead() =
        runTest {
            val secure = FakeSecureStore().also { it.entries["auth_refresh_token"] = "refresh-1" }
            val events = SessionEvents()
            val manager = SessionManager(SecureTokenStore(secure), events, backgroundScope)
            val observer = SessionManagerObserver(manager, events)

            assertFalse(manager.isRestored.value)
            runCurrent()
            assertEquals(SessionPhase.SignedIn, observer.phase.first())
        }

    @Test
    fun loginAndLogoutMoveThePhase() =
        runTest {
            val events = SessionEvents()
            val manager = SessionManager(SecureTokenStore(FakeSecureStore()), events, backgroundScope)
            val observer = SessionManagerObserver(manager, events)
            runCurrent()
            assertEquals(SessionPhase.SignedOut, observer.phase.first())

            manager.startSession("access-1", "refresh-1")
            assertEquals(SessionPhase.SignedIn, observer.phase.first())

            manager.endSession()
            assertEquals(SessionPhase.SignedOut, observer.phase.first())
        }

    @Test
    fun aRejectedRefreshTokenIsAnExpiredItemAndSignedOut() =
        runTest {
            val events = SessionEvents()
            val manager = SessionManager(SecureTokenStore(FakeSecureStore()), events, backgroundScope)
            val observer = SessionManagerObserver(manager, events)
            manager.startSession("access-1", "refresh-1")
            val expired = mutableListOf<Unit>()
            backgroundScope.launch { observer.expired.collect { expired += it } }
            runCurrent()

            events.emit(SessionEvent.SessionExpired)
            runCurrent()

            assertEquals(1, expired.size)
            assertEquals(SessionPhase.SignedOut, observer.phase.first())
        }
}
