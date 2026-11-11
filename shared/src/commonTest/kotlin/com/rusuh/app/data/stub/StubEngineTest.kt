package com.rusuh.app.data.stub

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.idempotencyKey
import com.rusuh.app.data.network.jsonBody
import com.rusuh.app.domain.model.ErrorCode
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class StubEngineTest {
    private val stub = StubEngine(recordRequests = true)
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
    fun aKnownPathUnderAnotherMethodIsA405MissNotARealNotFound() =
        runTest {
            val response = api.client.request("events") { method = HttpMethod.Post }

            assertEquals(405, response.status.value)
            assertEquals("true", response.headers[StubEngine.MISS_HEADER])
            val result = assertIs<ApiResult.Error>(api.post<JsonObject>("/events"))
            assertEquals(StubEngine.MISS_CODE, result.problem?.rawCode)
        }

    @Test
    fun anUnknownRouteIsA404MissNamingIt() =
        runTest {
            val result = assertIs<ApiResult.Error>(api.get<JsonObject>("/nope/1"))

            assertEquals(404, result.code)
            assertEquals(StubEngine.MISS_CODE, result.problem?.rawCode)
            assertEquals(true, result.problem?.detail.orEmpty().contains("GET /nope/1"))
            assertEquals("true", api.client.get("nope/1").headers[StubEngine.MISS_HEADER])
            assertEquals(null, api.client.get("events").headers[StubEngine.MISS_HEADER])
            stub.assertRequestLine("GET /nope/1", "GET /nope/1", "GET /events")
        }

    @Test
    fun aScriptedParameterRouteDoesNotShadowAMoreSpecificDefault() =
        runTest {
            stub.script(HttpMethod.Get, "/bookings/{bookingId}", StubError.NotFound)

            val active = api.get<JsonObject>("/bookings/active")
            val byId = api.get<JsonObject>("/bookings/bk_1")

            assertIs<ApiResult.Success<JsonObject>>(active)
            assertEquals(404, assertIs<ApiResult.Error>(byId).code)
        }

    @Test
    fun aScriptWinsATieWithTheDefaultRoute() =
        runTest {
            stub.script(HttpMethod.Get, "/events", StubError.ServiceUnavailable)

            assertEquals(503, assertIs<ApiResult.Error>(api.get<JsonObject>("/events")).code)
        }

    @Test
    fun pathParametersArePercentDecodedAndQueriesIgnored() =
        runTest {
            val result = api.get<JsonObject>("/tickets/tk%20a%2Fb?status=used&page=2")

            assertIs<ApiResult.Success<JsonObject>>(result)
            val request = stub.requests.single()
            assertEquals(mapOf("ticketId" to "tk a/b"), request.pathParams)
            assertEquals("status=used&page=2", request.query)
        }

    @Test
    fun deletesAndLogoutAnswer204WithNoBody() =
        runTest {
            assertEquals(204, assertIs<ApiResult.Success<Unit>>(api.delete<Unit>("/queue/evt_2026_gbk/me")).code)
            assertEquals(204, assertIs<ApiResult.Success<Unit>>(api.post<Unit>("/auth/logout")).code)
        }

    @Test
    fun paymentAndBookingActionsAreStubbed() =
        runTest {
            val cancel = assertIs<ApiResult.Success<JsonObject>>(api.post<JsonObject>("/bookings/bk_1/cancel"))
            val snap = assertIs<ApiResult.Success<JsonObject>>(api.post<JsonObject>("/payments/bk_1/snap"))
            val device = assertIs<ApiResult.Success<JsonObject>>(api.post<JsonObject>("/devices"))

            assertEquals("CANCELLED", cancel.data.text("status"))
            assertEquals(201, snap.code)
            assertEquals(201, device.code)
            assertEquals("2026-12-14T10:40:00Z", device.data.text("expiresAt"))
        }

    @Test
    fun nothingIsRecordedUnlessRecordingIsOn() =
        runTest {
            val quiet = StubEngine()

            quiet.apiClient().get<JsonObject>("/events")

            assertEquals(emptyList(), quiet.requests)
        }

    @Test
    fun theRequestLogKeepsOnlyTheLastEntries() =
        runTest {
            val bounded = StubEngine(recordRequests = true, maxRecordedRequests = 3)
            val client = bounded.apiClient()

            repeat(5) { client.get<JsonObject>("/tickets/tk_$it") }

            assertEquals(listOf("tk_2", "tk_3", "tk_4"), bounded.requests.map { it.pathParams.getValue("ticketId") })
        }

    @Test
    fun parallelRequestsConsumeEachScriptedResponseExactlyOnce() =
        runTest {
            val parallel = StubEngine(recordRequests = true, maxRecordedRequests = 50)
            val client = parallel.apiClient()
            val total = 200
            parallel.script(
                HttpMethod.Get,
                "/queue/{eventId}/frontier",
                frontierSequence(parallel.clock, *IntArray(total) { it }),
            )

            val served =
                withContext(Dispatchers.Default) {
                    (0 until total)
                        .map { async { frontierOf(client) } }
                        .awaitAll()
                }

            assertEquals((0 until total).toList(), served.sorted())
            assertEquals(50, parallel.requests.size)
            parallel.clock.advanceBy(1.minutes)
        }

    private suspend fun frontierOf(client: ApiClient): Int {
        val result = assertIs<ApiResult.Success<JsonObject>>(client.get<JsonObject>("/queue/evt_2026_gbk/frontier"))
        return (result.data.getValue("served") as JsonPrimitive).content.toInt()
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
