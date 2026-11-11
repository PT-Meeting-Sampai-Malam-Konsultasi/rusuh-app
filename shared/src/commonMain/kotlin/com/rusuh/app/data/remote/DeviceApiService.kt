package com.rusuh.app.data.remote

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.jsonBody
import com.rusuh.app.data.remote.dto.DeviceTokenDto
import com.rusuh.app.data.remote.dto.RegisterDeviceRequestDto

/** `POST /devices` (queue service). The path is sessionless: no Bearer, no refresh handling. */
class DeviceApiService(
    private val api: ApiClient,
) {
    /**
     * Registers this install and returns the signed device token.
     * TODO(EPIC-01-M5 #66 Turnstile/attestation): no `X-Turnstile-Token` or attestation is available on mobile yet,
     * so none is sent.
     */
    suspend fun register(body: RegisterDeviceRequestDto): ApiResult<DeviceTokenDto> =
        api.post(EP_DEVICES) { jsonBody(body) }

    private companion object {
        const val EP_DEVICES = "/devices"
    }
}
