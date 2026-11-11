package com.rusuh.app.data.remote.dto

import com.rusuh.app.domain.model.ErrorCode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * RFC 9457 problem details (`Problem` schema, `application/problem+json`).
 * Members outside the schema, such as `attemptsLeft`, `categoryId` or `nikMasked`, are kept in [extras].
 * Absent or mistyped members take their defaults; a body that is not a JSON object throws SerializationException.
 */
@Serializable(with = ProblemDetailsSerializer::class)
data class ProblemDetails(
    val type: String = "",
    val title: String = "",
    val status: Int = 0,
    val code: ErrorCode = ErrorCode.UNKNOWN,
    /** The wire value of `code` when it is not a known [ErrorCode]; null otherwise. For diagnostics. */
    val rawCode: String? = null,
    val detail: String? = null,
    val traceId: String? = null,
    val extras: Map<String, JsonElement> = emptyMap(),
)

object ProblemDetailsSerializer : KSerializer<ProblemDetails> {
    private const val TYPE = "type"
    private const val TITLE = "title"
    private const val STATUS = "status"
    private const val CODE = "code"
    private const val DETAIL = "detail"
    private const val TRACE_ID = "traceId"
    private val knownKeys = setOf(TYPE, TITLE, STATUS, CODE, DETAIL, TRACE_ID)

    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): ProblemDetails {
        val json =
            (decoder as JsonDecoder).decodeJsonElement() as? JsonObject
                ?: throw SerializationException("Problem body must be a JSON object")

        fun text(key: String): String? = (json[key] as? JsonPrimitive)?.contentOrNull
        val code = ErrorCode.fromWire(text(CODE))
        return ProblemDetails(
            type = text(TYPE).orEmpty(),
            title = text(TITLE).orEmpty(),
            status = (json[STATUS] as? JsonPrimitive)?.intOrNull ?: 0,
            code = code,
            rawCode = if (code == ErrorCode.UNKNOWN) text(CODE) else null,
            detail = text(DETAIL),
            traceId = text(TRACE_ID),
            extras = json.filterKeys { it !in knownKeys },
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: ProblemDetails,
    ) {
        val json =
            buildJsonObject {
                put(TYPE, value.type)
                put(TITLE, value.title)
                put(STATUS, value.status)
                put(CODE, value.rawCode ?: value.code.name)
                value.detail?.let { put(DETAIL, it) }
                value.traceId?.let { put(TRACE_ID, it) }
                value.extras.forEach { (key, element) -> put(key, element) }
            }
        (encoder as JsonEncoder).encodeJsonElement(json)
    }
}
