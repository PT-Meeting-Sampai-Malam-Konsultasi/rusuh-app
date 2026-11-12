package com.rusuh.app.data.remote

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.jsonBody
import com.rusuh.app.data.network.plugin.skipSessionAuth
import com.rusuh.app.data.remote.dto.OtpChallengeDto
import com.rusuh.app.data.remote.dto.RequestOtpDto
import com.rusuh.app.data.remote.dto.SessionDto
import com.rusuh.app.data.remote.dto.UserDto
import com.rusuh.app.data.remote.dto.VerifyOtpDto
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

/**
 * Auth endpoints of the core service. `POST /auth/otp` and `/auth/otp/verify` are session-less (device token only,
 * no Bearer, no 401 refresh: see `SESSIONLESS_PATHS`); `/me` carries the Bearer and gets
 * the single-flight 401 refresh. `POST /auth/refresh` is NOT here: `ApiTokenRefresher` owns it.
 *
 * Contract: the Apidog project "Rusuh Booking Tiket API" (no `/auth/otp/request` alias, no `/auth/profile`).
 */
class AuthApiService(
    private val api: ApiClient,
) {
    /**
     * TODO(EPIC-01-M5 #66): the contract gates `POST /auth/otp` with `X-Turnstile-Token` (web), `X-Queue-Token` (in the
     * waiting room) or `X-Admission-Token` (guest on the Detail page). None is sent yet; add optional parameters
     * (`queueToken()` / `admissionToken()` request builders exist) when the sign-in UI knows the context.
     */
    suspend fun requestOtp(body: RequestOtpDto): ApiResult<OtpChallengeDto> = api.post(EP_OTP) { jsonBody(body) }

    suspend fun verifyOtp(body: VerifyOtpDto): ApiResult<SessionDto> = api.post(EP_VERIFY) { jsonBody(body) }

    /**
     * 204. Revokes the token family on the server; needs the Bearer. [bearer] is the access token to send: the
     * repository passes a snapshot taken before it clears the local session, so the call can run after the sign-out.
     */
    suspend fun logout(bearer: String): ApiResult<Unit> =
        api.post(EP_LOGOUT) {
            // Outside the session handling: the explicit Bearer is the only credential, and a 401 must never be
            // replayed with the access token of a newer login.
            skipSessionAuth()
            header(HttpHeaders.Authorization, "Bearer $bearer")
        }

    suspend fun getMe(): ApiResult<UserDto> = api.get(EP_ME)

    private companion object {
        const val EP_OTP = "/auth/otp"
        const val EP_VERIFY = "/auth/otp/verify"
        const val EP_LOGOUT = "/auth/logout"
        const val EP_ME = "/me"
    }
}
