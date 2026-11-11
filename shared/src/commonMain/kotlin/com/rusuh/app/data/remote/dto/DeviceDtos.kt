package com.rusuh.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of `POST /devices`. Mobile sends `installId`; the attestation member is not sent yet. */
@Serializable
data class RegisterDeviceRequestDto(
    @SerialName("platform") val platform: String,
    @SerialName("installId") val installId: String,
) {
    override fun toString(): String = "RegisterDeviceRequestDto(platform=$platform, installId=<redacted>)"
}

/** Answer of `POST /devices` (201). `toString` hides the token. */
@Serializable
data class DeviceTokenDto(
    @SerialName("deviceToken") val deviceToken: String = "",
    @SerialName("expiresAt") val expiresAt: String? = null,
) {
    override fun toString(): String = "DeviceTokenDto(expiresAt=$expiresAt, deviceToken=<redacted>)"
}
