package com.rusuh.app.data.remote.dto

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.domain.model.ErrorCode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProblemDetailsTest {
    private val json = ApiClient.defaultJson

    @Test
    fun `every spec error code parses to its own value`() {
        specCodes.forEach { name ->
            assertEquals(name, ErrorCode.fromWire(name).name)
        }
        assertEquals(specCodes.size + 1, ErrorCode.entries.size)
    }

    @Test
    fun `unseen null and lowercase strings map to UNKNOWN`() {
        assertEquals(ErrorCode.UNKNOWN, ErrorCode.fromWire("SOMETHING_NEW"))
        assertEquals(ErrorCode.UNKNOWN, ErrorCode.fromWire(null))
        assertEquals(ErrorCode.UNKNOWN, ErrorCode.fromWire("not_found"))
    }

    @Test
    fun `parses the full schema and keeps unknown members as extras`() {
        val body =
            """{"type":"t","title":"T","status":429,"code":"RATE_LIMITED","detail":"d",""" +
                """"traceId":"id","attemptsLeft":5}"""

        val problem = json.decodeFromString(ProblemDetails.serializer(), body)

        assertEquals(
            ProblemDetails(
                "t",
                "T",
                429,
                ErrorCode.RATE_LIMITED,
                null,
                "d",
                "id",
                mapOf("attemptsLeft" to JsonPrimitive(5)),
            ),
            problem,
        )
    }

    @Test
    fun `missing and mistyped members fall back to defaults`() {
        val problem = json.decodeFromString(ProblemDetails.serializer(), """{"status":"x","code":7,"detail":null}""")

        assertEquals(ProblemDetails(status = 0, code = ErrorCode.UNKNOWN, rawCode = "7"), problem)
    }

    @Test
    fun `unknown code keeps its raw value and round trips`() {
        val problem = json.decodeFromString(ProblemDetails.serializer(), """{"status":409,"code":"BRAND_NEW"}""")
        val text = json.encodeToString(ProblemDetails.serializer(), problem)

        assertEquals(ErrorCode.UNKNOWN, problem.code)
        assertEquals("BRAND_NEW", problem.rawCode)
        assertEquals(problem, json.decodeFromString(ProblemDetails.serializer(), text))
    }

    @Test
    fun `a known code has no raw code`() {
        val problem = json.decodeFromString(ProblemDetails.serializer(), """{"code":"NOT_FOUND"}""")

        assertNull(problem.rawCode)
    }

    @Test
    fun `a body that is not an object throws SerializationException`() {
        assertFailsWith<SerializationException> { json.decodeFromString(ProblemDetails.serializer(), "[1]") }
    }

    @Test
    fun `serialization round trips`() {
        val problem =
            ProblemDetails(
                "t",
                "T",
                409,
                ErrorCode.NO_STOCK,
                null,
                "d",
                "id",
                mapOf("categoryId" to JsonPrimitive("c")),
            )

        val text = json.encodeToString(ProblemDetails.serializer(), problem)

        assertEquals(problem, json.decodeFromString(ProblemDetails.serializer(), text))
    }

    private companion object {
        val specCodes =
            listOf(
                "VALIDATION_FAILED", "INTERNAL_ERROR", "VERIFICATION_UNAVAILABLE", "UNAUTHORIZED", "ADMISSION_INVALID",
                "NOT_FOUND", "RATE_LIMITED", "QUEUE_CLOSED", "NOT_YET", "FULL", "MISSED", "EXPIRED", "NO_STOCK",
                "TOO_FAR", "STREAM_FULL", "ACCOUNT_HAS_PLACE_ELSEWHERE", "MOVED", "WRONG_CODE", "CHALLENGE_EXPIRED",
                "CHALLENGE_LOCKED", "NIK_INVALID", "NIK_SEAT_TAKEN", "DUPLICATE_NIK", "ATTENDEE_NOT_VALIDATED",
                "QUANTITY_OVER_RESERVATION", "DUKCAPIL_UNAVAILABLE", "ACCOUNT_HAS_ACTIVE_BOOKING",
                "CATEGORY_SOLD_OUT", "BOOKING_NOT_HELD", "BOOKING_NOT_PAID", "NOT_CANCELLABLE", "CODE_INVALID",
                "PAYMENT_PROVIDER_BUSY", "FORBIDDEN", "SERVICE_UNAVAILABLE", "TIKET_TIDAK_DITEMUKAN",
                "TIKET_SUDAH_DIGUNAKAN", "TIKET_TIDAK_BERLAKU",
            )
    }
}
