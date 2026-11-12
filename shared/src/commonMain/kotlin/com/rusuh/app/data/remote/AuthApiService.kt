package com.rusuh.app.data.remote

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.jsonBody
import com.rusuh.app.data.remote.dto.OtpChallengeDto
import com.rusuh.app.data.remote.dto.RequestOtpDto
import com.rusuh.app.data.remote.dto.SessionDto
import com.rusuh.app.data.remote.dto.UpdateProfileDto
import com.rusuh.app.data.remote.dto.UserDto
import com.rusuh.app.data.remote.dto.VerifyOtpDto

/**
 * Auth endpoints of the core service. `POST /auth/otp` and `/auth/otp/verify` are session-less (device token only,
 * no Bearer, no 401 refresh: see `SESSIONLESS_PATHS`); `/auth/logout` and `/auth/profile` carry the Bearer and get
 * the single-flight 401 refresh. `POST /auth/refresh` is NOT here: `ApiTokenRefresher` owns it.
 *
 * shortcut: the profile is read from the alias `GET /auth/profile`, not from the contract's `GET /me`, because the
 * live backend answers 404 for `/me` (verified 2026-11-12). Switch [EP_PROFILE] back to `/me` when it is routed.
 * The canonical `/auth/otp` is used for the code request, not its alias `/auth/otp/request`.
 */
class AuthApiService(
    private val api: ApiClient,
) {
    suspend fun requestOtp(body: RequestOtpDto): ApiResult<OtpChallengeDto> = api.post(EP_OTP) { jsonBody(body) }

    suspend fun verifyOtp(body: VerifyOtpDto): ApiResult<SessionDto> = api.post(EP_VERIFY) { jsonBody(body) }

    /** 204. Revokes the token family on the server; needs the Bearer. */
    suspend fun logout(): ApiResult<Unit> = api.post(EP_LOGOUT)

    suspend fun getProfile(): ApiResult<UserDto> = api.get(EP_PROFILE)

    suspend fun updateProfile(body: UpdateProfileDto): ApiResult<UserDto> = api.put(EP_PROFILE) { jsonBody(body) }

    private companion object {
        const val EP_OTP = "/auth/otp"
        const val EP_VERIFY = "/auth/otp/verify"
        const val EP_LOGOUT = "/auth/logout"
        const val EP_PROFILE = "/auth/profile"
    }
}
