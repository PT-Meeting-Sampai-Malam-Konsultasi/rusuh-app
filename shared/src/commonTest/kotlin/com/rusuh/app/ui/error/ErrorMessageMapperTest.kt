package com.rusuh.app.ui.error

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [ErrorMessageMapper]. Verifies that every ErrorCode maps to a resource key,
 * HTTP status classes map correctly, and DomainResult.Error mapping works as expected.
 */
class ErrorMessageMapperTest {
    @Test
    fun everyErrorCodeMapsToAKey() {
        // Every ErrorCode enum entry must map to a non-null ErrorMessageKey
        ErrorCode.entries.forEach { code ->
            val key = ErrorMessageMapper.keyFor(code)
            // The key's resourceName should match the error_code_<name> pattern
            val expectedPattern = "error_code_${code.name.lowercase()}"
            assertEquals(
                expectedPattern,
                key.resourceName,
                "ErrorCode.$code should map to resource key $expectedPattern",
            )
        }
    }

    @Test
    fun everyErrorCodeHasUniqueResourceName() {
        // No two ErrorCodes should map to the same resource name (except HTTP fallbacks)
        val resourceNames = ErrorCode.entries.map { ErrorMessageMapper.keyFor(it).resourceName }
        assertEquals(resourceNames.size, resourceNames.toSet().size, "All ErrorCode resource names should be unique")
    }

    @Test
    fun validationFailedMapsCorrectly() {
        val key = ErrorMessageMapper.keyFor(ErrorCode.VALIDATION_FAILED)
        assertEquals(ErrorMessageKey.VALIDATION_FAILED, key)
        assertEquals("error_code_validation_failed", key.resourceName)
    }

    @Test
    fun internalErrorMapsCorrectly() {
        val key = ErrorMessageMapper.keyFor(ErrorCode.INTERNAL_ERROR)
        assertEquals(ErrorMessageKey.INTERNAL_ERROR, key)
        assertEquals("error_code_internal_error", key.resourceName)
    }

    @Test
    fun unauthorizedMapsCorrectly() {
        val key = ErrorMessageMapper.keyFor(ErrorCode.UNAUTHORIZED)
        assertEquals(ErrorMessageKey.UNAUTHORIZED, key)
        assertEquals("error_code_unauthorized", key.resourceName)
    }

    @Test
    fun rateLimitedMapsCorrectly() {
        val key = ErrorMessageMapper.keyFor(ErrorCode.RATE_LIMITED)
        assertEquals(ErrorMessageKey.RATE_LIMITED, key)
        assertEquals("error_code_rate_limited", key.resourceName)
    }

    @Test
    fun nikInvalidMapsCorrectly() {
        val key = ErrorMessageMapper.keyFor(ErrorCode.NIK_INVALID)
        assertEquals(ErrorMessageKey.NIK_INVALID, key)
        assertEquals("error_code_nik_invalid", key.resourceName)
    }

    @Test
    fun wrongCodeMapsCorrectly() {
        val key = ErrorMessageMapper.keyFor(ErrorCode.WRONG_CODE)
        assertEquals(ErrorMessageKey.WRONG_CODE, key)
        assertEquals("error_code_wrong_code", key.resourceName)
    }

    @Test
    fun unknownErrorCodeMapsCorrectly() {
        val key = ErrorMessageMapper.keyFor(ErrorCode.UNKNOWN)
        assertEquals(ErrorMessageKey.UNKNOWN, key)
        assertEquals("error_code_unknown", key.resourceName)
    }

    // --- HTTP status class tests ---

    @Test
    fun http400MapsToClientError() {
        val key = ErrorMessageMapper.keyForHttpStatus(400)
        assertEquals(ErrorMessageKey.HTTP_CLIENT_ERROR, key)
        assertEquals("error_http_client", key.resourceName)
    }

    @Test
    fun http401MapsToClientError() {
        val key = ErrorMessageMapper.keyForHttpStatus(401)
        assertEquals(ErrorMessageKey.HTTP_CLIENT_ERROR, key)
    }

    @Test
    fun http403MapsToClientError() {
        val key = ErrorMessageMapper.keyForHttpStatus(403)
        assertEquals(ErrorMessageKey.HTTP_CLIENT_ERROR, key)
    }

    @Test
    fun http404MapsToClientError() {
        val key = ErrorMessageMapper.keyForHttpStatus(404)
        assertEquals(ErrorMessageKey.HTTP_CLIENT_ERROR, key)
    }

    @Test
    fun http422MapsToClientError() {
        val key = ErrorMessageMapper.keyForHttpStatus(422)
        assertEquals(ErrorMessageKey.HTTP_CLIENT_ERROR, key)
    }

    @Test
    fun http429MapsToClientError() {
        val key = ErrorMessageMapper.keyForHttpStatus(429)
        assertEquals(ErrorMessageKey.HTTP_CLIENT_ERROR, key)
    }

    @Test
    fun http499MapsToClientError() {
        val key = ErrorMessageMapper.keyForHttpStatus(499)
        assertEquals(ErrorMessageKey.HTTP_CLIENT_ERROR, key)
    }

    @Test
    fun http500MapsToServerError() {
        val key = ErrorMessageMapper.keyForHttpStatus(500)
        assertEquals(ErrorMessageKey.HTTP_SERVER_ERROR, key)
        assertEquals("error_http_server", key.resourceName)
    }

    @Test
    fun http502MapsToServerError() {
        val key = ErrorMessageMapper.keyForHttpStatus(502)
        assertEquals(ErrorMessageKey.HTTP_SERVER_ERROR, key)
    }

    @Test
    fun http503MapsToServerError() {
        val key = ErrorMessageMapper.keyForHttpStatus(503)
        assertEquals(ErrorMessageKey.HTTP_SERVER_ERROR, key)
    }

    @Test
    fun http504MapsToServerError() {
        val key = ErrorMessageMapper.keyForHttpStatus(504)
        assertEquals(ErrorMessageKey.HTTP_SERVER_ERROR, key)
    }

    @Test
    fun http599MapsToServerError() {
        val key = ErrorMessageMapper.keyForHttpStatus(599)
        assertEquals(ErrorMessageKey.HTTP_SERVER_ERROR, key)
    }

    @Test
    fun http200MapsToUnknown() {
        // 2xx status should not map to client or server error
        val key = ErrorMessageMapper.keyForHttpStatus(200)
        assertEquals(ErrorMessageKey.UNKNOWN, key)
    }

    @Test
    fun http300MapsToUnknown() {
        // 3xx status should not map to client or server error
        val key = ErrorMessageMapper.keyForHttpStatus(301)
        assertEquals(ErrorMessageKey.UNKNOWN, key)
    }

    @Test
    fun http0MapsToUnknown() {
        val key = ErrorMessageMapper.keyForHttpStatus(0)
        assertEquals(ErrorMessageKey.UNKNOWN, key)
    }

    // --- DomainResult.Error tests ---

    @Test
    fun domainResultErrorWithSpecificCodeUsesErrorCode() {
        val error =
            DomainResult.Error(
                code = 400,
                errorCode = ErrorCode.VALIDATION_FAILED,
            )
        val key = ErrorMessageMapper.keyFor(error)
        assertEquals(ErrorMessageKey.VALIDATION_FAILED, key)
    }

    @Test
    fun domainResultErrorWithUnknownCodeFallsBackToHttpStatus() {
        val error =
            DomainResult.Error(
                code = 500,
                errorCode = ErrorCode.UNKNOWN,
            )
        val key = ErrorMessageMapper.keyFor(error)
        assertEquals(ErrorMessageKey.HTTP_SERVER_ERROR, key)
    }

    @Test
    fun domainResultError401WithUnknownCodeFallsBackToHttpStatus() {
        val error =
            DomainResult.Error(
                code = 401,
                errorCode = ErrorCode.UNKNOWN,
            )
        val key = ErrorMessageMapper.keyFor(error)
        assertEquals(ErrorMessageKey.HTTP_CLIENT_ERROR, key)
    }

    @Test
    fun domainResultError404WithNotFoundCodeUsesErrorCode() {
        val error =
            DomainResult.Error(
                code = 404,
                errorCode = ErrorCode.NOT_FOUND,
            )
        val key = ErrorMessageMapper.keyFor(error)
        assertEquals(ErrorMessageKey.NOT_FOUND, key)
    }

    @Test
    fun domainResultError503WithServiceUnavailableUsesErrorCode() {
        val error =
            DomainResult.Error(
                code = 503,
                errorCode = ErrorCode.SERVICE_UNAVAILABLE,
            )
        val key = ErrorMessageMapper.keyFor(error)
        assertEquals(ErrorMessageKey.SERVICE_UNAVAILABLE, key)
    }

    @Test
    fun domainResultErrorWithNoCodeFallsBackToUnknown() {
        val error =
            DomainResult.Error(
                code = 0,
                errorCode = ErrorCode.UNKNOWN,
            )
        val key = ErrorMessageMapper.keyFor(error)
        assertEquals(ErrorMessageKey.UNKNOWN, key)
    }

    // --- ErrorMessageKey resource name tests ---

    @Test
    fun allErrorCodeKeysFollowNamingConvention() {
        ErrorMessageKey.entries
            .filter { it != ErrorMessageKey.HTTP_CLIENT_ERROR && it != ErrorMessageKey.HTTP_SERVER_ERROR }
            .forEach { key ->
                assertTrue(
                    key.resourceName.startsWith("error_code_"),
                    "${key.name} should have resourceName starting with error_code_",
                )
            }
    }

    @Test
    fun httpFallbackKeysFollowNamingConvention() {
        assertEquals("error_http_client", ErrorMessageKey.HTTP_CLIENT_ERROR.resourceName)
        assertEquals("error_http_server", ErrorMessageKey.HTTP_SERVER_ERROR.resourceName)
    }
}
