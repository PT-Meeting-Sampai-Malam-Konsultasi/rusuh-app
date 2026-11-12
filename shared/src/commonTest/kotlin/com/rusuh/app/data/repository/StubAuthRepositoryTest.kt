package com.rusuh.app.data.repository

import com.rusuh.app.data.network.session.SessionEvent
import com.rusuh.app.data.network.session.SessionEvents
import com.rusuh.app.data.network.session.SessionManager
import com.rusuh.app.data.storage.FakeSecureStore
import com.rusuh.app.data.storage.SecureTokenStore
import com.rusuh.app.data.stub.StubClock
import com.rusuh.app.domain.model.AuthFailure
import com.rusuh.app.domain.model.AuthSession
import com.rusuh.app.domain.model.AuthUser
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.OtpChallenge
import com.rusuh.app.domain.model.authFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class StubAuthRepositoryTest {
    private class Fixture(scope: TestScope) {
        val clock = StubClock()
        val tokens = SecureTokenStore(FakeSecureStore())
        val events = SessionEvents()
        val session = SessionManager(tokens, events, scope.backgroundScope)
        val repository = StubAuthRepository(session, clock)
    }

    private val email = "stub.user@example.test"

    private suspend fun StubAuthRepository.challenge(
        fullName: String? = null,
        address: String = email,
    ): OtpChallenge = assertIs<DomainResult.Success<OtpChallenge>>(requestOtp(address, fullName)).data

    private suspend fun StubAuthRepository.signIn(
        fullName: String? = null,
        address: String = email,
    ): AuthSession =
        assertIs<DomainResult.Success<AuthSession>>(
            verifyOtp(challenge(fullName, address).challengeId, StubAuthRepository.CODE_OK),
        ).data

    @Test
    fun theRightCodeSignsInAndStoresPlaceholderTokens() =
        runTest {
            val f = Fixture(this)

            val session = f.repository.signIn("Stub User")

            assertEquals("Stub User", session.user.fullName)
            assertEquals(email, session.user.email)
            assertTrue(session.isNewAccount)
            assertTrue(f.session.isSignedIn.value)
            assertTrue(f.tokens.accessToken != null && f.tokens.getRefreshToken() != null)
        }

    @Test
    fun aNameLessFirstSignInIsANewAccountWithoutAName() =
        runTest {
            val f = Fixture(this)

            val session = f.repository.signIn()

            assertTrue(session.isNewAccount)
            assertNull(session.user.fullName)
            assertNull(assertIs<DomainResult.Success<AuthUser>>(f.repository.me()).data.fullName)
        }

    @Test
    fun theSecondSignInOfTheSameEmailIsNotANewAccount() =
        runTest {
            val f = Fixture(this)
            f.repository.signIn("Stub User")
            f.repository.logout()

            val again = f.repository.signIn()

            assertFalse(again.isNewAccount)
            assertEquals("Stub User", again.user.fullName)
        }

    @Test
    fun wrongCodesCountTheAttemptsDownThenLock() =
        runTest {
            val f = Fixture(this)
            val challenge = f.repository.challenge()

            val left =
                (1 until StubAuthRepository.MAX_ATTEMPTS).map {
                    f.repository.verifyOtp(challenge.challengeId, "111111").authFailure()
                }

            assertEquals((4 downTo 1).map { AuthFailure.WrongCode(it) }, left)
            assertEquals(
                AuthFailure.ChallengeLocked,
                f.repository.verifyOtp(challenge.challengeId, "111111").authFailure(),
            )
            assertEquals(
                AuthFailure.ChallengeLocked,
                f.repository.verifyOtp(challenge.challengeId, StubAuthRepository.CODE_OK).authFailure(),
                "a locked challenge stays locked even for the right code",
            )
            assertFalse(f.session.isSignedIn.value)
        }

    @Test
    fun theExpiredCodeAndAnUnknownChallengeAnswerExpired() =
        runTest {
            val f = Fixture(this)
            val challenge = f.repository.challenge()

            assertEquals(
                AuthFailure.ChallengeExpired,
                f.repository.verifyOtp(challenge.challengeId, StubAuthRepository.CODE_EXPIRED).authFailure(),
            )
            assertEquals(
                AuthFailure.ChallengeExpired,
                f.repository.verifyOtp("ch_nope", StubAuthRepository.CODE_OK).authFailure(),
            )
        }

    @Test
    fun aChallengeExpiresWithTheClock() =
        runTest {
            val f = Fixture(this)
            val challenge = f.repository.challenge()

            f.clock.advanceBy(11.minutes)

            assertEquals(
                AuthFailure.ChallengeExpired,
                f.repository.verifyOtp(challenge.challengeId, StubAuthRepository.CODE_OK).authFailure(),
            )
        }

    @Test
    fun everyRequestGetsItsOwnChallengeAndAnInvalidEmailIsRejected() =
        runTest {
            val f = Fixture(this)

            assertNotEquals(f.repository.challenge().challengeId, f.repository.challenge().challengeId)
            assertEquals(AuthFailure.InvalidInput, f.repository.requestOtp("not-an-email").authFailure())
        }

    @Test
    fun logoutSignsOutAndTheProfileIsUnauthorizedAgain() =
        runTest {
            val f = Fixture(this)
            f.repository.signIn("Stub User")

            assertEquals(DomainResult.Success(Unit), f.repository.logout())

            assertFalse(f.session.isSignedIn.value)
            assertNull(f.tokens.accessToken)
            assertEquals(AuthFailure.Unauthorized, f.repository.me().authFailure())
        }

    @Test
    fun meAfterASessionExpiryIsUnauthorized() =
        runTest {
            val f = Fixture(this)
            f.repository.signIn("Stub User")
            assertIs<DomainResult.Success<AuthUser>>(f.repository.me())

            f.events.emit(SessionEvent.SessionExpired)
            runCurrent()

            assertEquals(AuthFailure.Unauthorized, f.repository.me().authFailure())
        }
}
