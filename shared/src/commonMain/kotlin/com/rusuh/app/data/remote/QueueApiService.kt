package com.rusuh.app.data.remote

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.plugin.queueToken
import com.rusuh.app.data.remote.dto.AdmissionDto
import com.rusuh.app.data.remote.dto.FrontierDto
import com.rusuh.app.data.remote.dto.QueuePlaceDto
import io.ktor.http.encodeURLPathPart

/**
 * The queue endpoints (queue service). The device token and the optional Bearer are added by the host client; the
 * per-event queue token is passed per call where the contract asks for it.
 */
class QueueApiService(
    internal val api: ApiClient,
) {
    /** Idempotent per device: returns the existing place or takes the next number. */
    suspend fun join(eventId: String): ApiResult<QueuePlaceDto> = api.post(path(eventId, JOIN))

    /** Public and identical for every device; no token. */
    suspend fun frontier(eventId: String): ApiResult<FrontierDto> = api.get(path(eventId, FRONTIER))

    suspend fun me(
        eventId: String,
        queueToken: String,
    ): ApiResult<QueuePlaceDto> = api.get(path(eventId, ME)) { queueToken(queueToken) }

    /** Needs a signed-in account (401 for a guest). A retry returns the same admission. */
    suspend fun claim(
        eventId: String,
        queueToken: String,
    ): ApiResult<AdmissionDto> = api.post(path(eventId, CLAIM)) { queueToken(queueToken) }

    /** Idempotent: 204 even when the place already ended. */
    suspend fun leave(
        eventId: String,
        queueToken: String,
    ): ApiResult<Unit> = api.delete(path(eventId, ME)) { queueToken(queueToken) }

    private fun path(
        eventId: String,
        suffix: String,
    ) = "/queue/${eventId.encodeURLPathPart()}/$suffix"

    private companion object {
        const val JOIN = "join"
        const val FRONTIER = "frontier"
        const val ME = "me"
        const val CLAIM = "claim"
    }
}
