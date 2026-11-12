package com.rusuh.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of `POST /queue/devices`. */
@Serializable
data class RegisterDeviceRequestDto(
    @SerialName("platform") val platform: String,
    @SerialName("installId") val installId: String,
    @SerialName("attestation") val attestation: String,
) {
    override fun toString(): String = "RegisterDeviceRequestDto(platform=$platform, installId=<redacted>)"
}

/** Answer of `POST /queue/devices` (201). `toString` hides the token. */
@Serializable
data class DeviceTokenDto(
    @SerialName("deviceToken") val deviceToken: String = "",
    @SerialName("expiresAt") val expiresAt: String? = null,
) {
    override fun toString(): String = "DeviceTokenDto(expiresAt=$expiresAt, deviceToken=<redacted>)"
}
