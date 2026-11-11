package com.rusuh.app.data.storage

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SecureTokenStoreTest {
    private val secure = FakeSecureStore()
    private val tokens = SecureTokenStore(secure)

    @Test
    fun accessTokenIsMemoryOnly() =
        runTest {
            tokens.accessToken = ACCESS
            assertEquals(ACCESS, tokens.accessToken)
            assertFalse(secure.entries.values.contains(ACCESS))
            assertEquals(0, secure.entries.size)
        }

    @Test
    fun refreshDeviceAndAdmissionTokensArePersistedThroughSecureStore() =
        runTest {
            tokens.setRefreshToken("r1")
            tokens.setDeviceToken("d1")
            tokens.setAdmissionToken("a1")

            val reopened = SecureTokenStore(secure)
            assertEquals("r1", reopened.getRefreshToken())
            assertEquals("d1", reopened.getDeviceToken())
            assertEquals("a1", reopened.getAdmissionToken())
            assertNull(reopened.accessToken)
        }

    @Test
    fun queueTokensAreIsolatedPerEvent() =
        runTest {
            tokens.setQueueToken("evt-1", "q1")
            tokens.setQueueToken("evt-2", "q2")

            assertEquals("q1", tokens.getQueueToken("evt-1"))
            assertEquals("q2", tokens.getQueueToken("evt-2"))
            assertNull(tokens.getQueueToken("evt-3"))
            assertEquals("q1", secure.entries["queue_token_evt-1"])

            tokens.clearQueueToken("evt-1")
            assertNull(tokens.getQueueToken("evt-1"))
            assertEquals("q2", tokens.getQueueToken("evt-2"))
        }

    @Test
    fun blankEventIdIsRejected() =
        runTest {
            assertFailsWith<IllegalArgumentException> { tokens.getQueueToken(" ") }
        }

    @Test
    fun clearSessionDropsSessionSecretsButKeepsDeviceToken() =
        runTest {
            tokens.accessToken = ACCESS
            tokens.setRefreshToken("r1")
            tokens.setDeviceToken("d1")
            tokens.setAdmissionToken("a1")
            tokens.setQueueToken("evt-1", "q1")
            tokens.setQueueToken("evt-2", "q2")
            tokens.setQueueToken("evt-3", "q3")

            tokens.clearSession()

            assertNull(tokens.accessToken)
            assertNull(tokens.getRefreshToken())
            assertNull(tokens.getAdmissionToken())
            assertNull(tokens.getQueueToken("evt-1"))
            assertNull(tokens.getQueueToken("evt-2"))
            assertNull(tokens.getQueueToken("evt-3"))
            assertEquals("d1", tokens.getDeviceToken())
            assertEquals(setOf("auth_device_token"), secure.entries.keys)
        }

    @Test
    fun clearSessionKeepsGoingWhenOneRemovalThrowsAndRethrowsTheFirstFailure() =
        runTest {
            tokens.accessToken = ACCESS
            tokens.setRefreshToken("r1")
            tokens.setDeviceToken("d1")
            tokens.setAdmissionToken("a1")
            tokens.setQueueToken("evt-1", "q1")
            secure.failingRemovals += "auth_refresh_token"

            val failure = assertFailsWith<IllegalStateException> { tokens.clearSession() }

            assertEquals("remove failed", failure.message)
            assertNull(tokens.accessToken)
            assertNull(tokens.getAdmissionToken())
            assertNull(tokens.getQueueToken("evt-1"))
            assertEquals("d1", tokens.getDeviceToken())
        }

    @Test
    fun clearSessionStillClearsAccessTokenWhenEveryRemovalThrows() =
        runTest {
            tokens.accessToken = ACCESS
            secure.failingRemovals += setOf("auth_refresh_token", "book_admission_token", "queue_token_")

            assertFailsWith<IllegalStateException> { tokens.clearSession() }

            assertNull(tokens.accessToken)
        }

    @Test
    fun deviceTokenSetConcurrentlyWithClearSessionIsNotLost() =
        runTest {
            secure.yieldOnWrite = true
            repeat(RACE_RUNS) { i ->
                val device = "d$i"
                val writer = launch { tokens.setDeviceToken(device) }
                val logout = launch { tokens.clearSession() }
                writer.join()
                logout.join()
                assertEquals(device, tokens.getDeviceToken())
            }
        }

    @Test
    fun clearSessionWithoutAnyTokensLeavesStoreEmpty() =
        runTest {
            tokens.clearSession()
            assertEquals(0, secure.entries.size)
        }

    private companion object {
        const val ACCESS = "access-secret"
        const val RACE_RUNS = 20
    }
}
