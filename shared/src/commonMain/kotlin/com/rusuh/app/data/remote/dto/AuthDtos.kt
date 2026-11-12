package com.rusuh.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body of `POST /auth/otp`. shortcut: `consentVersion` is deliberately NOT a member: the live server rejects it
 * (400 `"consentVersion" is not allowed`, verified 2026-11-12) although the contract file lists it as optional.
 * Add it back (and to `AuthRepository.requestOtp`) when the backend accepts it. `toString` hides the email and the
 * name (personal data, never logged).
 */
@Serializable
data class RequestOtpDto(
    @SerialName("email") val email: String = "",
    @SerialName("fullName") val fullName: String? = null,
    @SerialName("locale") val locale: String? = null,
) {
    override fun toString(): String = "RequestOtpDto(<redacted>)"
}

/** Answer of `POST /auth/otp` (202). */
@Serializable
data class OtpChallengeDto(
    @SerialName("challengeId") val challengeId: String = "",
    @SerialName("expiresAt") val expiresAt: String? = null,
    @SerialName("resendAfterSeconds") val resendAfterSeconds: Int = 0,
)

/** Body of `POST /auth/otp/verify`. `toString` hides the code. */
@Serializable
data class VerifyOtpDto(
    @SerialName("challengeId") val challengeId: String = "",
    @SerialName("code") val code: String = "",
) {
    override fun toString(): String = "VerifyOtpDto(challengeId=$challengeId, code=<redacted>)"
}

/** `User` schema (profile). */
@Serializable
data class UserDto(
    @SerialName("userId") val userId: String = "",
    @SerialName("email") val email: String = "",
    @SerialName("fullName") val fullName: String? = null,
    @SerialName("locale") val locale: String? = null,
) {
    override fun toString(): String = "UserDto(userId=$userId, <redacted>)"
}

/** `Session` schema, the answer of `POST /auth/otp/verify`. `toString` hides the tokens and the user. */
@Serializable
data class SessionDto(
    @SerialName("accessToken") val accessToken: String = "",
    @SerialName("expiresIn") val expiresIn: Int = 0,
    @SerialName("refreshToken") val refreshToken: String? = null,
    @SerialName("isNewAccount") val isNewAccount: Boolean = false,
    @SerialName("user") val user: UserDto = UserDto(),
) {
    override fun toString(): String = "SessionDto(isNewAccount=$isNewAccount, <redacted>)"
}

/** Body of `PUT /auth/profile`. */
@Serializable
data class UpdateProfileDto(
    @SerialName("fullName") val fullName: String = "",
    @SerialName("locale") val locale: String? = null,
) {
    override fun toString(): String = "UpdateProfileDto(<redacted>)"
}
