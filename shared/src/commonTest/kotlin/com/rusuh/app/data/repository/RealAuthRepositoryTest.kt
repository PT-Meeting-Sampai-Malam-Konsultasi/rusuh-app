package com.rusuh.app.data.repository

import com.rusuh.app.data.network.session.SessionEvent
import com.rusuh.app.domain.model.AuthFailure
import com.rusuh.app.domain.model.AuthSession
import com.rusuh.app.domain.model.AuthUser
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import com.rusuh.app.domain.model.OtpChallenge
import com.rusuh.app.domain.model.UserLocale
import com.rusuh.app.domain.model.authFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RealAuthRepositoryTest {
    private val otp = "POST /auth/otp"
    private val verify = "POST /auth/otp/verify"
    private val logout = "POST /auth/logout"
    private val profile = "GET /auth/profile"
    private val refresh = "POST /auth/refresh"

    private fun TestScope.harness(devices: FakeDevices = FakeDevices()) = AuthHarness(backgroundScope, devices)

    private fun Seen.bearer() = headers["Authorization"]

    private fun Seen.json() = Json.parseToJsonElement(body).jsonObject

    // requestOtp

    @Test
    fun requestOtpSendsTheBodyToTheCanonicalPathAndMapsTheChallenge() =
        runTest {
            val h = harness()
            h.backend.on(otp, Reply(202, CHALLENGE_JSON))

            val result =
                h.repository.requestOtp("test.user@example.test", "Test User", UserLocale.English)

            val challenge = assertIs<DomainResult.Success<*>>(result).data
            assertEquals(
                OtpChallenge("ch_test_1", 1_794_653_400_000L, 60),
                challenge,
            )
            val seen = h.backend.seen.single()
            assertEquals(otp, seen.line)
            assertEquals("test.user@example.test", seen.json().getValue("email").jsonPrimitive.content)
            assertEquals("Test User", seen.json().getValue("fullName").jsonPrimitive.content)
            assertFalse("consentVersion" in seen.body, "the live server rejects it")
            assertEquals(setOf("email", "fullName", "locale"), seen.json().keys)
            assertEquals("en", seen.json().getValue("locale").jsonPrimitive.content)
            assertNull(seen.bearer(), "the code request is session-less")
            assertEquals(1, h.devices.calls, "the device token is made sure first")
        }

    @Test
    fun requestOtpOmitsTheOptionalMembersThatAreNotGiven() =
        runTest {
            val h = harness()
            h.backend.on(otp, Reply(202, CHALLENGE_JSON))

            h.repository.requestOtp("test.user@example.test", fullName = "  ")

            assertEquals(setOf("email"), h.backend.seen.single().json().keys)
        }

    @Test
    fun requestOtpWithoutAChallengeIdIsATechnicalError() =
        runTest {
            val h = harness()
            h.backend.on(otp, Reply(202, """{"expiresAt":"2026-11-14T10:50:00Z"}"""))

            assertEquals(DomainResult.TechnicalError, h.repository.requestOtp("test.user@example.test"))
        }

    @Test
    fun requestOtpMapsTheLiveBackendValidationProblem() =
        runTest {
            val h = harness()
            h.backend.on(otp, problemReply(400, LIVE_BAD_REQUEST))

            val result = assertIs<DomainResult.Error>(h.repository.requestOtp(""))

            assertEquals(400, result.code)
            assertEquals(ErrorCode.UNKNOWN, result.errorCode, "BAD_REQUEST is not in the contract")
            assertEquals("Validasi gagal: \"email\" is required", result.message)
            assertEquals(AuthFailure.InvalidInput, result.authFailure())
        }

    @Test
    fun requestOtpMapsTheContractValidationProblem() =
        runTest {
            val h = harness()
            h.backend.on(otp, codeReply(400, "VALIDATION_FAILED"))

            assertEquals(AuthFailure.InvalidInput, h.repository.requestOtp("x").authFailure())
        }

    @Test
    fun requestOtpRateLimitedCarriesRetryAfter() =
        runTest {
            val h = harness()
            h.backend.on(otp, codeReply(429, "RATE_LIMITED").let { problemReply(429, it.body, retryAfter = 45) })

            val result = h.repository.requestOtp("test.user@example.test")

            assertEquals(AuthFailure.RateLimited(45), result.authFailure())
            assertEquals(45, assertIs<DomainResult.Error>(result).retryAfterSeconds)
        }

    @Test
    fun requestOtpOfflineIsANetworkError() =
        runTest {
            val h = harness()
            h.backend.on(otp, Reply.offline())

            val result = h.repository.requestOtp("test.user@example.test")

            assertEquals(DomainResult.NetworkError, result)
            assertEquals(AuthFailure.Network, result.authFailure())
        }

    @Test
    fun aFailedDeviceRegistrationStopsTheCodeRequestAndTheCodeCheck() =
        runTest {
            val failure = DomainResult.Error(code = 503, errorCode = ErrorCode.SERVICE_UNAVAILABLE)
            val h = harness(FakeDevices(failure))

            assertEquals(failure, h.repository.requestOtp("test.user@example.test"))
            assertEquals(failure, h.repository.verifyOtp("ch_test_1", "123456"))
            assertTrue(h.backend.seen.isEmpty())
        }

    // verifyOtp

    @Test
    fun verifyOtpStoresTheTokensLikeTheRefreshLayerAndSignsIn() =
        runTest {
            val h = harness()
            h.events.emit(SessionEvent.SessionExpired)
            h.backend.on(verify, Reply.ok(SESSION_JSON))

            val result = h.repository.verifyOtp("ch_test_1", "123456")

            val session = assertIs<DomainResult.Success<AuthSession>>(result).data
            assertEquals("usr_test_1", session.user.userId)
            assertEquals("Test User", session.user.fullName)
            assertEquals(UserLocale.Indonesian, session.user.locale)
            assertFalse(session.isNewAccount)
            assertEquals(900, session.accessTokenExpiresInSeconds)
            assertEquals("access-1", h.tokens.accessToken)
            assertEquals("refresh-1", h.tokens.getRefreshToken())
            assertEquals("refresh-1", h.secure.entries["auth_refresh_token"])
            assertTrue(h.session.isSignedIn.value)
            assertFalse(h.events.expired.value, "a login clears the sticky expired state")
            val seen = h.backend.seen.single()
            assertEquals("ch_test_1", seen.json().getValue("challengeId").jsonPrimitive.content)
            assertEquals("123456", seen.json().getValue("code").jsonPrimitive.content)
            assertNull(seen.bearer())
        }

    @Test
    fun verifyOtpOfANewAccountWithoutANameIsFlaggedSoTheUiCanAskForIt() =
        runTest {
            val h = harness()
            h.backend.on(
                verify,
                Reply.ok(
                    """{"accessToken":"access-1","expiresIn":900,"refreshToken":"refresh-1","isNewAccount":true,""" +
                        """"user":{"userId":"usr_test_2","email":"new.user@example.test"}}""",
                ),
            )

            val session =
                assertIs<DomainResult.Success<AuthSession>>(
                    h.repository.verifyOtp("ch_test_1", "123456"),
                ).data

            assertTrue(session.isNewAccount)
            assertNull(session.user.fullName)
            assertNull(session.user.locale)
        }

    @Test
    fun verifyOtpWrongCodeCarriesAttemptsLeftAndStoresNothing() =
        runTest {
            val h = harness()
            h.backend.on(verify, wrongCodeReply(3))

            val result = h.repository.verifyOtp("ch_test_1", "111111")

            assertEquals(AuthFailure.WrongCode(3), result.authFailure())
            assertEquals(409, assertIs<DomainResult.Error>(result).code)
            assertNull(h.tokens.accessToken)
            assertNull(h.tokens.getRefreshToken())
            assertFalse(h.session.isSignedIn.value)
        }

    @Test
    fun verifyOtpMapsTheLiveExpiredBodyByCodeNotByTitle() =
        runTest {
            val h = harness()
            h.backend.on(verify, problemReply(410, LIVE_EXPIRED))

            val result = h.repository.verifyOtp("ch_nope", "123456")

            assertEquals(AuthFailure.ChallengeExpired, result.authFailure())
            assertEquals(ErrorCode.CHALLENGE_EXPIRED, assertIs<DomainResult.Error>(result).errorCode)
        }

    @Test
    fun verifyOtpExpiredAndLockedAreDistinct() =
        runTest {
            val h = harness()
            h.backend.on(verify, codeReply(410, "CHALLENGE_EXPIRED"), codeReply(410, "CHALLENGE_LOCKED"))

            assertEquals(AuthFailure.ChallengeExpired, h.repository.verifyOtp("c", "123456").authFailure())
            assertEquals(AuthFailure.ChallengeLocked, h.repository.verifyOtp("c", "123456").authFailure())
            assertFalse(h.session.isSignedIn.value)
        }

    @Test
    fun verifyOtpRateLimitedAndUnknownCodes() =
        runTest {
            val h = harness()
            h.backend.on(
                verify,
                problemReply(429, codeReply(429, "RATE_LIMITED").body, retryAfter = 10),
                codeReply(500, "SOMETHING_NEW"),
            )

            assertEquals(AuthFailure.RateLimited(10), h.repository.verifyOtp("c", "123456").authFailure())
            val unknown = h.repository.verifyOtp("c", "123456")
            assertEquals(AuthFailure.Unknown(500), unknown.authFailure())
            assertEquals(ErrorCode.UNKNOWN, assertIs<DomainResult.Error>(unknown).errorCode)
        }

    @Test
    fun verifyOtpUnauthorizedFromTheLiveBackendDoesNotRefreshOrExpireAnything() =
        runTest {
            val h = harness()
            h.seedSession()
            h.backend.on(verify, problemReply(401, LIVE_UNAUTHORIZED))

            val result = h.repository.verifyOtp("c", "123456")

            assertEquals(AuthFailure.Unauthorized, result.authFailure())
            assertEquals(ErrorCode.UNAUTHORIZED, assertIs<DomainResult.Error>(result).errorCode)
            assertEquals(0, h.backend.callsTo(refresh), "session-less: a 401 here is not an expired session")
            assertNull(h.backend.seen.single().bearer())
            assertFalse(h.events.expired.value)
        }

    @Test
    fun verifyOtpWithoutAnAccessTokenOrUserIsATechnicalErrorAndNoSession() =
        runTest {
            val h = harness()
            h.backend.on(
                verify,
                Reply.ok("""{"accessToken":"","expiresIn":900,"user":$USER_JSON}"""),
                Reply.ok("""{"accessToken":"access-1","expiresIn":900,"user":{}}"""),
            )

            assertEquals(DomainResult.TechnicalError, h.repository.verifyOtp("c", "123456"))
            assertEquals(DomainResult.TechnicalError, h.repository.verifyOtp("c", "123456"))
            assertNull(h.tokens.accessToken)
            assertFalse(h.session.isSignedIn.value)
        }

    @Test
    fun verifyOtpWithoutARefreshTokenKeepsTheStoredOneAndSignsIn() =
        runTest {
            val h = harness()
            h.seedSession(access = null, refresh = "refresh-old")
            h.backend.on(verify, Reply.ok("""{"accessToken":"access-1","expiresIn":900,"user":$USER_JSON}"""))

            assertIs<DomainResult.Success<*>>(h.repository.verifyOtp("c", "123456"))

            assertEquals("refresh-old", h.tokens.getRefreshToken())
            assertTrue(h.session.isSignedIn.value)
        }

    // logout

    @Test
    fun logoutCallsTheServerWithTheBearerThenClearsEverythingButTheDeviceToken() =
        runTest {
            val h = harness()
            h.backend.on(verify, Reply.ok(SESSION_JSON))
            h.backend.on(logout, Reply.noContent())
            h.repository.verifyOtp("c", "123456")
            h.tokens.setDeviceToken("device-1")

            val result = h.repository.logout()

            assertEquals(DomainResult.Success(Unit), result)
            val call = h.backend.seen.last()
            assertEquals(logout, call.line)
            assertEquals("Bearer access-1", call.bearer(), "logout is NOT session-less")
            assertNull(h.tokens.accessToken)
            assertNull(h.tokens.getRefreshToken())
            assertEquals("device-1", h.tokens.getDeviceToken())
            assertFalse(h.session.isSignedIn.value)
        }

    @Test
    fun logoutClearsLocallyEvenWhenTheServerFailsOrIsOffline() =
        runTest {
            listOf(Reply(500, ""), Reply.offline(), Reply.notFound()).forEach { reply ->
                val h = harness()
                h.seedSession()
                h.session.startSession("access-1", "refresh-1")
                h.backend.on(logout, reply)

                val result = h.repository.logout()

                assertTrue(result !is DomainResult.Success, "$reply")
                assertNull(h.tokens.accessToken)
                assertNull(h.tokens.getRefreshToken())
                assertFalse(h.session.isSignedIn.value)
            }
        }

    @Test
    fun logoutClearsTheFlagEvenWhenTheStorageRemovalThrows() =
        runTest {
            val h = harness()
            h.seedSession()
            h.session.startSession("access-1", "refresh-1")
            h.backend.on(logout, Reply.noContent())
            h.secure.failingRemovals += "auth_refresh_token"

            val result = h.repository.logout()

            assertEquals(DomainResult.Success(Unit), result)
            assertFalse(h.session.isSignedIn.value)
            assertNull(h.tokens.accessToken, "the access token goes first")
        }

    @Test
    fun logoutWithARejectedRefreshStillSignsOutAndExpiresTheSessionOnce() =
        runTest {
            val h = harness()
            h.seedSession()
            h.session.startSession("access-1", "refresh-1")
            h.backend.on(logout, problemReply(401, LIVE_UNAUTHORIZED))
            h.backend.on(refresh, problemReply(401, LIVE_UNAUTHORIZED))

            val result = h.repository.logout()

            assertEquals(AuthFailure.Unauthorized, result.authFailure())
            assertFalse(h.session.isSignedIn.value)
            assertNull(h.tokens.getRefreshToken())
        }

    // me and updateProfile

    @Test
    fun meReadsTheAliasProfilePathWithTheBearerNeverMe() =
        runTest {
            val h = harness()
            h.seedSession()
            h.backend.on(profile, Reply.ok(USER_JSON))

            val user = assertIs<DomainResult.Success<AuthUser>>(h.repository.me()).data

            assertEquals("usr_test_1", user.userId)
            assertEquals("test.user@example.test", user.email)
            assertEquals("Bearer access-1", h.backend.seen.single().bearer())
            assertEquals(0, h.backend.callsTo("GET /me"))
        }

    @Test
    fun meWithTheLiveUnauthorizedBodyExpiresTheSessionAfterTheRefreshIsRejected() =
        runTest {
            val h = harness()
            h.seedSession()
            h.session.startSession("access-1", "refresh-1")
            h.backend.on(profile, problemReply(401, LIVE_UNAUTHORIZED))
            h.backend.on(refresh, problemReply(401, LIVE_UNAUTHORIZED))

            val result = h.repository.me()

            assertEquals(AuthFailure.Unauthorized, result.authFailure())
            withContext(Dispatchers.Default) {
                withTimeout(5.seconds) { h.session.isSignedIn.first { !it } }
            }
            assertTrue(h.events.expired.value)
            assertNull(h.tokens.getRefreshToken())
        }

    @Test
    fun anExpiredAccessTokenRefreshesWithTheStoredRefreshTokenAndRetriesOnce() =
        runTest {
            val h = harness()
            h.backend.on(verify, Reply.ok(SESSION_JSON))
            h.backend.on(profile, problemReply(401, LIVE_UNAUTHORIZED), Reply.ok(USER_JSON))
            h.backend.on(refresh, Reply.ok(REFRESHED_JSON))
            h.repository.verifyOtp("c", "123456")

            assertIs<DomainResult.Success<*>>(h.repository.me())

            val refreshCall = h.backend.seen.single { it.line == refresh }
            assertEquals("refresh-1", refreshCall.json().getValue("refreshToken").jsonPrimitive.content)
            assertNull(refreshCall.bearer())
            assertEquals("access-2", h.tokens.accessToken)
            assertEquals("refresh-2", h.tokens.getRefreshToken())
            assertTrue(h.session.isSignedIn.value)
            assertEquals("Bearer access-2", h.backend.seen.last().bearer())
        }

    @Test
    fun updateProfilePutsTheNameAndMapsTheUser() =
        runTest {
            val h = harness()
            h.seedSession()
            h.backend.on("PUT /auth/profile", Reply.ok(USER_JSON))

            val result = h.repository.updateProfile("Test User", UserLocale.English)

            assertIs<DomainResult.Success<*>>(result)
            val seen = h.backend.seen.single()
            assertEquals("Test User", seen.json().getValue("fullName").jsonPrimitive.content)
            assertEquals("en", seen.json().getValue("locale").jsonPrimitive.content)
            assertEquals("Bearer access-1", seen.bearer())
        }

    @Test
    fun updateProfileValidationFailureIsInvalidInput() =
        runTest {
            val h = harness()
            h.seedSession()
            h.backend.on("PUT /auth/profile", problemReply(400, LIVE_BAD_REQUEST))

            assertEquals(AuthFailure.InvalidInput, h.repository.updateProfile("").authFailure())
        }

    @Test
    fun theLoginAfterAnExpiryClearsItAndFlipsTheFlagBack() =
        runTest {
            val h = harness()
            h.seedSession()
            h.session.startSession("access-1", "refresh-1")
            runCurrent()
            h.events.emit(SessionEvent.SessionExpired)
            runCurrent()
            assertFalse(h.session.isSignedIn.value)
            h.backend.on(verify, Reply.ok(SESSION_JSON))

            h.repository.verifyOtp("c", "123456")
            runCurrent()

            assertTrue(h.session.isSignedIn.value)
            assertFalse(h.events.expired.value)
        }
}
