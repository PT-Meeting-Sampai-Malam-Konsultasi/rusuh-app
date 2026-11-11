package com.rusuh.app.data.network.session

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.jsonBody
import com.rusuh.app.data.network.plugin.skipSessionAuth
import com.rusuh.app.data.remote.dto.RefreshRequestDto
import com.rusuh.app.data.remote.dto.RefreshResponseDto
import io.ktor.http.HttpStatusCode

/**
 * Default [TokenRefresher]: `POST /auth/refresh` with the refresh token in the body (mobile). The call carries only
 * the device token (the contract's security for it): no Bearer and no 401 handling, so it cannot loop.
 */
class ApiTokenRefresher(
    private val api: ApiClient,
) : TokenRefresher {
    override suspend fun refresh(refreshToken: String): RefreshOutcome {
        val result =
            api.post<RefreshResponseDto>(EP_REFRESH) {
                skipSessionAuth()
                jsonBody(RefreshRequestDto(refreshToken))
            }
        return when {
            result is ApiResult.Success && result.data.accessToken.isNotBlank() ->
                RefreshOutcome.Refreshed(result.data.accessToken, result.data.refreshToken?.takeIf { it.isNotBlank() })
            result is ApiResult.Error && result.code in REJECTED_CODES -> RefreshOutcome.Rejected
            else -> RefreshOutcome.Unavailable
        }
    }

    private companion object {
        const val EP_REFRESH = "/auth/refresh"
        val REJECTED_CODES = setOf(HttpStatusCode.Unauthorized.value, HttpStatusCode.BadRequest.value)
    }
}
