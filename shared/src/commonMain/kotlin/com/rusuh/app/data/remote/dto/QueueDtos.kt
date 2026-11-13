package com.rusuh.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `QueuePlace` schema (answer of join and `GET /queue/{eventId}/me`). `toString` hides the queue token. */
@Serializable
data class QueuePlaceDto(
    @SerialName("eventId") val eventId: String = "",
    @SerialName("state") val state: String = "",
    @SerialName("seq") val seq: Int? = null,
    @SerialName("served") val served: Int? = null,
    @SerialName("opensAt") val opensAt: String? = null,
    @SerialName("turnExpiresAt") val turnExpiresAt: String? = null,
    @SerialName("serverNow") val serverNow: String? = null,
    @SerialName("queueToken") val queueToken: String = "",
) {
    override fun toString(): String = "QueuePlaceDto(eventId=$eventId, state=$state, seq=$seq, queueToken=<redacted>)"
}

/** `Frontier` schema. `opensAt` and `ts` are Unix seconds here (unlike the RFC 3339 strings of the place). */
@Serializable
data class FrontierDto(
    @SerialName("event") val event: String = "",
    @SerialName("state") val state: String = "",
    @SerialName("served") val served: Int = 0,
    @SerialName("ratePerMin") val ratePerMin: Int? = null,
    @SerialName("streamFrom") val streamFrom: Int? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("opensAt") val opensAt: Long? = null,
    @SerialName("ts") val ts: Long? = null,
)

/** `Admission` schema (answer of claim). `toString` hides the token. */
@Serializable
data class AdmissionDto(
    @SerialName("admissionToken") val admissionToken: String = "",
    @SerialName("slotId") val slotId: String = "",
    @SerialName("maxTickets") val maxTickets: Int = 0,
    @SerialName("expiresAt") val expiresAt: String? = null,
    @SerialName("serverNow") val serverNow: String? = null,
) {
    override fun toString(): String =
        "AdmissionDto(slotId=$slotId, maxTickets=$maxTickets, expiresAt=$expiresAt, admissionToken=<redacted>)"
}

/** Body of `POST /queue/{eventId}/join`. */
@Serializable
data class JoinQueueRequestDto(
    @SerialName("takeOver") val takeOver: Boolean = false,
)

/** `turn` event payload from the SSE stream. */
@Serializable
data class QueueTurnDto(
    @SerialName("event") val event: String = "",
    @SerialName("seq") val seq: Int = 0,
    @SerialName("served") val served: Int = 0,
    @SerialName("turnExpiresAt") val turnExpiresAt: String? = null,
    @SerialName("serverNow") val serverNow: String? = null,
)
