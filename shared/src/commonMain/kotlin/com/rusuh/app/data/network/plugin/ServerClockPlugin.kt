@file:Suppress("MatchingDeclarationName") // file holds the plugin value; its Config class is the only class

package com.rusuh.app.data.network.plugin

import com.rusuh.app.data.clock.ServerTimeSink
import com.rusuh.app.data.clock.parseStringDateMillis
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.TimeMark

class ServerClockConfig {
    lateinit var sink: ServerTimeSink
}

/**
 * Records `serverNow` (RFC 3339, in the JSON body of countdown responses) into the [ServerTimeSink]. The send time of
 * each request is marked (request attribute) so the sink can compensate half the round trip and drop responses that
 * were sent before the current anchor. Only small `application/json` bodies are inspected (streams and downloads are
 * skipped; the cap applies to the received byte count, not only to `Content-Length`) and only when the text contains
 * `"serverNow"`, so other responses cost nothing. A missing or malformed value is ignored.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
val ServerClockPlugin =
    createClientPlugin("RusuhServerClock", ::ServerClockConfig) {
        val sink = pluginConfig.sink
        onRequest { request, _ -> request.attributes.put(SentAtKey, sink.markNow()) }
        onResponse { response ->
            val isJson = response.contentType()?.match(ContentType.Application.Json) == true
            val size = response.contentLength()
            if (isJson && (size == null || size <= MAX_BODY_BYTES)) {
                try {
                    val bytes = response.readRawBytes()
                    // A retried request lost its mark (see SessionRefreshPlugin): treat it as sent just now.
                    val sentAt = response.call.request.attributes.getOrNull(SentAtKey) ?: sink.markNow()
                    if (bytes.size <= MAX_BODY_BYTES) {
                        findServerNow(bytes.decodeToString())?.let { sink.recordServerNow(it, sentAt) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (unreadable: Exception) {
                    // Best effort: a body that cannot be read here fails (or not) in the caller's own decode.
                }
            }
        }
    }

internal val SentAtKey = AttributeKey<TimeMark>("rusuh.requestSentAt")
private const val MAX_BODY_BYTES = 256 * 1024
private const val SERVER_NOW = "serverNow"

/**
 * `serverNow` as epoch millis: at the top level, in a direct member object, or in the first element of a member array
 * that has it (`GET /bookings/active` answers `{"items":[{..., "serverNow": ...}]}`).
 */
internal fun findServerNow(body: String): Long? {
    val root = if (body.contains("\"$SERVER_NOW\"")) Json.parseToJsonElement(body) as? JsonObject else null
    val text =
        root?.let { serverNowOf(it) }
            ?: root?.values?.firstNotNullOfOrNull { member ->
                when (member) {
                    is JsonObject -> serverNowOf(member)
                    is JsonArray -> member.firstNotNullOfOrNull { (it as? JsonObject)?.let(::serverNowOf) }
                    else -> null
                }
            }
    return text?.let(::parseStringDateMillis)
}

private fun serverNowOf(obj: JsonObject): String? = (obj[SERVER_NOW] as? JsonPrimitive)?.takeIf { it.isString }?.content
