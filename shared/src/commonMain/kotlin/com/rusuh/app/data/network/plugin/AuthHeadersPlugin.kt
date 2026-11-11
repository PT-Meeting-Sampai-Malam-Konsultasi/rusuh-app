@file:Suppress("MatchingDeclarationName") // file holds the plugin value; its Config class is the only class

package com.rusuh.app.data.network.plugin

import com.rusuh.app.data.storage.TokenStore
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.encodedPath
import io.ktor.util.AttributeKey

class AuthHeadersConfig {
    lateinit var tokenStore: TokenStore
    var sendDeviceToken: Boolean = true
    var sendBearer: Boolean = true

    /** Called when a request is built while an access token exists (a login happened). */
    var onSignedIn: (() -> Unit)? = null
}

/**
 * Adds `X-Device-Token` and `Authorization: Bearer <access>` when the token exists. A header the call set itself is
 * never replaced. `X-Queue-Token` and `X-Admission-Token` are NOT added here: ApiServices opt in per call with
 * [queueToken] and [admissionToken]. Token values are never logged.
 */
val AuthHeadersPlugin =
    createClientPlugin("RusuhAuthHeaders", ::AuthHeadersConfig) {
        val store = pluginConfig.tokenStore
        val sendDevice = pluginConfig.sendDeviceToken
        val sendBearer = pluginConfig.sendBearer
        val onSignedIn = pluginConfig.onSignedIn
        onRequest { request, _ ->
            if (sendDevice && !request.headers.contains(DEVICE_TOKEN_HEADER)) {
                store.getDeviceToken()?.let { request.header(DEVICE_TOKEN_HEADER, it) }
            }
            val access = store.accessToken
            if (access != null) onSignedIn?.invoke()
            if (sendBearer && !request.isOutsideSession() && !request.headers.contains(HttpHeaders.Authorization)) {
                access?.let { request.header(HttpHeaders.Authorization, "$BEARER_PREFIX$it") }
            }
        }
    }

/**
 * Marks a call as outside the session: no Bearer header, no 401 refresh and no `SessionExpired`. The auth and device
 * endpoints ([SESSIONLESS_PATHS]) are outside the session by default; use this for any other such call.
 */
fun HttpRequestBuilder.skipSessionAuth() {
    attributes.put(SkipSessionAuthKey, true)
}

/** Sends the per-event queue token (`X-Queue-Token`). Opt in per call; never automatic. */
fun HttpRequestBuilder.queueToken(token: String) {
    header(QUEUE_TOKEN_HEADER, token)
}

/** Sends the admission token (`X-Admission-Token`). Opt in per call; never automatic. */
fun HttpRequestBuilder.admissionToken(token: String) {
    header(ADMISSION_TOKEN_HEADER, token)
}

/** True for calls that must neither carry a Bearer nor trigger refresh handling. */
internal fun HttpRequestBuilder.isOutsideSession(): Boolean {
    val path = "/" + url.encodedPath.trim('/')
    return attributes.contains(SkipSessionAuthKey) || SESSIONLESS_PATHS.any { path.endsWith(it) }
}

/** Their contract security is the device token only (or none): a 401 there is a bad code, not an expired session. */
internal val SESSIONLESS_PATHS =
    listOf("/auth/otp", "/auth/otp/verify", "/auth/otp/request", "/auth/refresh", "/devices")

internal val SkipSessionAuthKey = AttributeKey<Boolean>("rusuh.skipSessionAuth")
internal const val DEVICE_TOKEN_HEADER = "X-Device-Token"
internal const val QUEUE_TOKEN_HEADER = "X-Queue-Token"
internal const val ADMISSION_TOKEN_HEADER = "X-Admission-Token"
internal const val BEARER_PREFIX = "Bearer "
