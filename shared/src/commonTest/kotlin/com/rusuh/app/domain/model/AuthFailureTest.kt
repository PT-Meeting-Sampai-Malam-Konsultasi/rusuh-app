package com.rusuh.app.domain.model

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.remote.dto.ProblemDetails
import com.rusuh.app.data.repository.LIVE_BAD_REQUEST
import com.rusuh.app.data.repository.LIVE_EXPIRED
import com.rusuh.app.data.repository.LIVE_UNAUTHORIZED
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthFailureTest {
    private fun error(
        code: Int,
        errorCode: ErrorCode,
        extras: Map<String, JsonElement> = emptyMap(),
        retryAfter: Int? = null,
    ) = DomainResult.Error(code = code, errorCode = errorCode, extras = extras, retryAfterSeconds = retryAfter)

    @Test
    fun theContractCodesMapToTheirFailures() {
        assertEquals(
            AuthFailure.WrongCode(3),
            error(400, ErrorCode.WRONG_CODE, mapOf("attemptsLeft" to JsonPrimitive(3))).authFailure(),
        )
        assertEquals(AuthFailure.WrongCode(null), error(400, ErrorCode.WRONG_CODE).authFailure())
        assertEquals(AuthFailure.ChallengeExpired, error(410, ErrorCode.CHALLENGE_EXPIRED).authFailure())
        assertEquals(AuthFailure.ChallengeLocked, error(410, ErrorCode.CHALLENGE_LOCKED).authFailure())
        assertEquals(AuthFailure.RateLimited(30), error(429, ErrorCode.RATE_LIMITED, retryAfter = 30).authFailure())
        assertEquals(AuthFailure.InvalidInput, error(400, ErrorCode.VALIDATION_FAILED).authFailure())
        assertEquals(AuthFailure.Unauthorized, error(401, ErrorCode.UNAUTHORIZED).authFailure())
    }

    @Test
    fun anUnknownCodeFallsBackToTheHttpStatusForTheCommonCases() {
        assertEquals(AuthFailure.InvalidInput, error(400, ErrorCode.UNKNOWN).authFailure())
        assertEquals(AuthFailure.Unauthorized, error(401, ErrorCode.UNKNOWN).authFailure())
        assertEquals(AuthFailure.RateLimited(null), error(429, ErrorCode.UNKNOWN).authFailure())
        assertEquals(AuthFailure.ChallengeExpired, error(410, ErrorCode.UNKNOWN).authFailure())
        assertEquals(AuthFailure.Unknown(500), error(500, ErrorCode.UNKNOWN).authFailure())
        assertEquals(AuthFailure.WrongCode(null), error(409, ErrorCode.UNKNOWN).authFailure())
    }

    @Test
    fun aNonNumericAttemptsLeftIsNull() {
        val failure = error(400, ErrorCode.WRONG_CODE, mapOf("attemptsLeft" to JsonPrimitive("many"))).authFailure()

        assertEquals(AuthFailure.WrongCode(null), failure)
    }

    @Test
    fun transportFailuresAndNonFailures() {
        assertEquals(AuthFailure.Network, DomainResult.NetworkError.authFailure())
        assertEquals(AuthFailure.Unknown(0), DomainResult.TechnicalError.authFailure())
        assertNull(DomainResult.Success(Unit).authFailure())
        assertNull(DomainResult.Empty.authFailure())
    }

    @Test
    fun theLiveBackendBodiesParseWithTheirAboutBlankTypeAndMapToTheRightFailures() {
        val json = ApiClient.defaultJson

        val bad = json.decodeFromString(ProblemDetails.serializer(), LIVE_BAD_REQUEST)
        val unauthorized = json.decodeFromString(ProblemDetails.serializer(), LIVE_UNAUTHORIZED)

        assertEquals("about:blank", bad.type)
        assertEquals(400, bad.status)
        assertEquals(ErrorCode.UNKNOWN, bad.code)
        assertEquals("BAD_REQUEST", bad.rawCode)
        assertEquals("Validasi gagal: \"email\" is required", bad.detail)
        assertEquals("about:blank", unauthorized.type)
        assertEquals(ErrorCode.UNAUTHORIZED, unauthorized.code)
        assertNull(unauthorized.rawCode)
        assertEquals("Token akun tidak ditemukan", unauthorized.detail)
        assertEquals(AuthFailure.InvalidInput, error(bad.status, bad.code).authFailure())
        assertEquals(AuthFailure.Unauthorized, error(unauthorized.status, unauthorized.code).authFailure())
    }

    @Test
    fun theLiveExpiredBodyMapsByCodeAlthoughItsTitleDiffersFromTheContract() {
        val problem = ApiClient.defaultJson.decodeFromString(ProblemDetails.serializer(), LIVE_EXPIRED)

        assertEquals("OTP challenge expired", problem.title)
        assertEquals(ErrorCode.CHALLENGE_EXPIRED, problem.code)
        assertEquals(AuthFailure.ChallengeExpired, error(problem.status, problem.code).authFailure())
    }

    @Test
    fun theContractTypeUriStyleParsesToo() {
        val body =
            """{"type":"https://api.rusuh.id/problems/wrong-code","title":"Kode salah","status":400,""" +
                """"code":"WRONG_CODE","detail":"The code does not match.","attemptsLeft":3}"""

        val problem = ApiClient.defaultJson.decodeFromString(ProblemDetails.serializer(), body)

        assertEquals("https://api.rusuh.id/problems/wrong-code", problem.type)
        assertEquals(ErrorCode.WRONG_CODE, problem.code)
        assertEquals(JsonPrimitive(3), problem.extras["attemptsLeft"])
    }
}
