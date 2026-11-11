package com.rusuh.app.data.stub

import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.idempotencyKey
import com.rusuh.app.data.network.jsonBody
import com.rusuh.app.domain.model.ErrorCode
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class StubEngineTest {
    private val stub = StubEngine()
    private val api = stub.apiClient()

    @Serializable
    private data class Attendee(
        val fullName: String,
    )

    private fun JsonObject.text(name: String) = (getValue(name) as JsonPrimitive).content

    private suspend fun frontier(): Int {
        val result = api.get<JsonObject>("/queue/evt_2026_gbk/frontier")
        return (
            assertIs<ApiResult.Success<JsonObject>>(
                result,
            ).data.getValue("served") as JsonPrimitive
        ).content.toInt()
    }

    @Test
    fun servesTheDefaultResponseOfAParameterisedPath() =
        runTest {
            val result = api.get<JsonObject>("/events/evt_2026_gbk/categories")

            assertEquals(200, assertIs<ApiResult.Success<JsonObject>>(result).code)
            assertEquals("evt_2026_gbk", result.data.text("eventId"))
            assertEquals(mapOf("eventId" to "evt_2026_gbk"), stub.requests.single().pathParams)
        }

    @Test
    fun aLiteralSegmentBeatsAParameter() =
        runTest {
            val active = assertIs<ApiResult.Success<JsonObject>>(api.get<JsonObject>("/bookings/active"))
            val byId = assertIs<ApiResult.Success<JsonObject>>(api.get<JsonObject>("/bookings/bk_01JB2Y6Z8Q"))

            assertEquals(true, "items" in active.data)
            assertEquals("PAID", byId.data.text("status"))
            assertEquals(mapOf("bookingId" to "bk_01JB2Y6Z8Q"), stub.requests[1].pathParams)
        }

    @Test
    fun statusFollowsTheRouteNotJustTheBody() =
        runTest {
            assertEquals(201, assertIs<ApiResult.Success<JsonObject>>(api.post<JsonObject>("/bookings")).code)
            assertEquals(202, assertIs<ApiResult.Success<JsonObject>>(api.post<JsonObject>("/auth/otp")).code)
        }

    @Test
    fun methodMustMatchToo() =
        runTest {
            val result = api.post<JsonObject>("/events")

            assertEquals(404, assertIs<ApiResult.Error>(result).code)
            assertEquals(ErrorCode.NOT_FOUND, result.problem?.code)
        }

    @Test
    fun anUnknownRouteIsA404ProblemNamingIt() =
        runTest {
            val result = assertIs<ApiResult.Error>(api.get<JsonObject>("/nope/1"))

            assertEquals(404, result.code)
            assertEquals(true, result.problem?.detail.orEmpty().contains("GET /nope/1"))
            stub.assertRequestLine("GET /nope/1")
        }

    @Test
    fun timesAreRelativeToTheInjectedClock() =
        runTest {
            val first = assertIs<ApiResult.Success<JsonObject>>(api.get<JsonObject>("/events/evt_2026_gbk/categories"))
            assertEquals("2026-11-14T10:40:00Z", first.data.text("serverNow"))
            assertEquals("2026-11-14T10:45:00Z", first.data.text("expiresAt"))

            stub.clock.advanceBy(2.minutes)
            val later = assertIs<ApiResult.Success<JsonObject>>(api.get<JsonObject>("/events/evt_2026_gbk/categories"))

            assertEquals("2026-11-14T10:42:00Z", later.data.text("serverNow"))
            assertEquals("2026-11-14T10:47:00Z", later.data.text("expiresAt"))
        }

    @Test
    fun scriptedFrontierRisesThenHolds() =
        runTest {
            stub.script(HttpMethod.Get, "/queue/{eventId}/frontier", frontierSequence(stub.clock, 100, 140, 190))

            assertEquals(listOf(100, 140, 190, 190), List(4) { frontier() })
            stub.assertRequest(0, HttpMethod.Get, "/queue/evt_2026_gbk/frontier")
        }

    @Test
    fun scriptingTheSameRouteAgainReplacesIt() =
        runTest {
            stub.script(HttpMethod.Get, "/queue/{eventId}/frontier", frontierSequence(stub.clock, 1))
            stub.script(HttpMethod.Get, "/queue/{eventId}/frontier", frontierSequence(stub.clock, 2))

            assertEquals(2, frontier())
        }

    @Test
    fun conflictProblemKeepsItsExtras() =
        runTest {
            stub.script(HttpMethod.Post, "/bookings", StubError.QuantityOverReservation)

            val result = assertIs<ApiResult.Error>(api.post<JsonObject>("/bookings"))

            assertEquals(409, result.code)
            assertEquals(ErrorCode.QUANTITY_OVER_RESERVATION, result.problem?.code)
            assertEquals(JsonPrimitive("cat_festival_b"), result.problem?.extras?.get("categoryId"))
            assertNull(result.retryAfterSeconds)
        }

    @Test
    fun rateLimitCarriesRetryAfter() =
        runTest {
            stub.script(HttpMethod.Post, "/queue/{eventId}/join", StubError.RateLimited)

            val result = assertIs<ApiResult.Error>(api.post<JsonObject>("/queue/evt_2026_gbk/join"))

            assertEquals(429, result.code)
            assertEquals(30, result.retryAfterSeconds)
            assertEquals(ErrorCode.RATE_LIMITED, result.problem?.code)
        }

    @Test
    fun serviceUnavailableThenRecovery() =
        runTest {
            stub.script(
                HttpMethod.Get,
                "/events",
                listOf(StubError.ServiceUnavailable.withRetryAfter(5), StubResponse.ok(StubJson.EVENTS)),
            )

            val down = assertIs<ApiResult.Error>(api.get<JsonObject>("/events"))
            val up = api.get<JsonObject>("/events")

            assertEquals(503, down.code)
            assertEquals(5, down.retryAfterSeconds)
            assertIs<ApiResult.Success<JsonObject>>(up)
        }

    @Test
    fun logRecordsMethodPathQueryHeadersAndBody() =
        runTest {
            api.post<JsonObject>("/events/evt_2026_gbk/attendees/validate") {
                idempotencyKey("key-1")
                jsonBody(Attendee("Rina Wulandari"))
            }
            api.get<JsonObject>("/tickets")

            val first =
                stub.assertRequest(
                    0,
                    HttpMethod.Post,
                    "/events/evt_2026_gbk/attendees/validate",
                    mapOf("Idempotency-Key" to "key-1", "Accept" to "application/json"),
                )
            assertEquals("""{"fullName":"Rina Wulandari"}""", first.body)
            stub.assertRequest(1, HttpMethod.Get, "/tickets")
            stub.assertRequestLine("POST /events/evt_2026_gbk/attendees/validate", "GET /tickets")
        }

    @Test
    fun resetClearsScriptsAndLog() =
        runTest {
            stub.script(HttpMethod.Get, "/events", StubError.ServiceUnavailable)
            api.get<JsonObject>("/events")

            stub.reset()

            assertEquals(emptyList(), stub.requests)
            assertIs<ApiResult.Success<JsonObject>>(api.get<JsonObject>("/events"))
        }
}
