package com.rusuh.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of `POST /auth/refresh` (mobile sends the refresh token in the body). `toString` hides the token. */
@Serializable
data class RefreshRequestDto(
    @SerialName("refreshToken") val refreshToken: String = "",
) {
    override fun toString(): String = "RefreshRequestDto(refreshToken=<redacted>)"
}

/** The token part of the `Session` answer of `POST /auth/refresh`; the rest is read by the auth data layer (#67). */
@Serializable
data class RefreshResponseDto(
    @SerialName("accessToken") val accessToken: String = "",
    @SerialName("refreshToken") val refreshToken: String? = null,
) {
    override fun toString(): String = "RefreshResponseDto(tokens=<redacted>)"
}
