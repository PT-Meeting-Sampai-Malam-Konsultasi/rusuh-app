@file:Suppress("MatchingDeclarationName") // file holds the plugin value; its Config class is the only class

package com.rusuh.app.data.network.plugin

import com.rusuh.app.data.clock.ServerTimeSink
import com.rusuh.app.data.clock.parseRfc3339Millis
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentLength
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ServerClockConfig {
    lateinit var sink: ServerTimeSink
}

/**
 * Records `serverNow` (RFC 3339, in the JSON body of every countdown response) into the [ServerTimeSink]. Only small
 * `application/json` bodies are inspected (streams and downloads are skipped) and only when the text contains
 * `"serverNow"`, so other responses cost nothing. A missing or malformed value is ignored.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
val ServerClockPlugin =
    createClientPlugin("RusuhServerClock", ::ServerClockConfig) {
        val sink = pluginConfig.sink
        onResponse { response ->
            val isJson = response.contentType()?.match(ContentType.Application.Json) == true
            val size = response.contentLength()
            if (isJson && (size == null || size <= MAX_BODY_BYTES)) {
                try {
                    findServerNow(response.bodyAsText())?.let(sink::recordServerNow)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (unreadable: Exception) {
                    // Best effort: a body that cannot be read here fails (or not) in the caller's own decode.
                }
            }
        }
    }

private const val MAX_BODY_BYTES = 256 * 1024L
private const val SERVER_NOW = "serverNow"

/** `serverNow` of the top-level object, or of a direct member object, as epoch millis. */
internal fun findServerNow(body: String): Long? {
    val root = if (body.contains("\"$SERVER_NOW\"")) Json.parseToJsonElement(body) as? JsonObject else null
    val text =
        root?.let { (it[SERVER_NOW] as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }
            ?: root?.values?.firstNotNullOfOrNull { value ->
                ((value as? JsonObject)?.get(SERVER_NOW) as? JsonPrimitive)?.takeIf { it.isString }?.content
            }
    return text?.let(::parseRfc3339Millis)
}
