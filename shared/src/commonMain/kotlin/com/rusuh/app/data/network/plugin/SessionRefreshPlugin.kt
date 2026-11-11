@file:Suppress("MatchingDeclarationName") // file holds the plugin value; its Config class is the only class

package com.rusuh.app.data.network.plugin

import com.rusuh.app.data.network.session.SessionRefreshCoordinator
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.util.AttributeKey

class SessionRefreshConfig {
    lateinit var coordinator: SessionRefreshCoordinator
}

/**
 * Handles `401`: one single-flight refresh through [SessionRefreshCoordinator], then the SAME request is sent once
 * more with the new access token. A second 401 is returned as is, so there is no loop. Only the request that the
 * server already rejected with 401 is replayed, and a 401 means it was not processed, so this never repeats a write.
 * Calls marked [skipSessionAuth] (the refresh call itself) are left alone.
 */
val SessionRefreshPlugin =
    createClientPlugin("RusuhSessionRefresh", ::SessionRefreshConfig) {
        val coordinator = pluginConfig.coordinator
        on(Send) { request ->
            val call = proceed(request)
            if (call.response.status != HttpStatusCode.Unauthorized ||
                request.attributes.contains(SkipSessionAuthKey) ||
                request.attributes.contains(RetriedKey)
            ) {
                return@on call
            }
            val usedToken = request.headers[HttpHeaders.Authorization]?.removePrefix(BEARER_PREFIX)
            val fresh = coordinator.refreshAfterUnauthorized(usedToken) ?: return@on call
            request.attributes.put(RetriedKey, true)
            request.headers.remove(HttpHeaders.Authorization)
            request.header(HttpHeaders.Authorization, "$BEARER_PREFIX$fresh")
            proceed(request)
        }
    }

private val RetriedKey = AttributeKey<Boolean>("rusuh.sessionRetried")
