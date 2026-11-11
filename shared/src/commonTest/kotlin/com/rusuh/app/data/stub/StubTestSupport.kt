package com.rusuh.app.data.stub

import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// Shared helpers for tests that run the real ApiClient and repositories on a [StubEngine]:
//
//   val stub = StubEngine()
//   val api = stub.apiClient()
//   stub.script(HttpMethod.Get, "/queue/{eventId}/frontier", frontierSequence(stub.clock, 100, 140, 190))
//   ...
//   stub.assertRequest(0, HttpMethod.Get, "/queue/evt_1/frontier", mapOf("Accept" to "application/json"))

/** Asserts the [index]th recorded request: [method], [path] and, when given, each header in [headers]. */
fun StubEngine.assertRequest(
    index: Int,
    method: HttpMethod,
    path: String,
    headers: Map<String, String> = emptyMap(),
): RecordedRequest {
    val request = requests.getOrNull(index)
    assertNotNull(request, "No request #$index; received ${requests.map { "${it.method.value} ${it.path}" }}")
    assertEquals(method, request.method, "method of request #$index")
    assertEquals(path, request.path, "path of request #$index")
    headers.forEach { (name, value) ->
        assertEquals(value, request.header(name), "header $name of request #$index")
    }
    return request
}

/** Asserts the engine received exactly [expected] (`"GET /events"`), in order. */
fun StubEngine.assertRequestLine(vararg expected: String) {
    assertEquals(expected.toList(), requests.map { "${it.method.value} ${it.path}" })
}

/**
 * A scripted `GET /queue/{eventId}/frontier` sequence: one 200 per [served] value (the last repeats), e.g.
 * `frontierSequence(clock, 100, 140, 190)` for a frontier that keeps rising.
 */
fun frontierSequence(
    clock: StubClock,
    vararg served: Int,
): List<StubResponse> =
    served.map {
            value ->
        StubResponse.ok(withMember(clock.render(StubJson.QUEUE_FRONTIER), "served", JsonPrimitive(value)))
    }

/** [body] (rendered JSON object) with member [name] replaced by [value]. */
fun withMember(
    body: String,
    name: String,
    value: JsonElement,
): String = JsonObject(Json.parseToJsonElement(body).jsonObject + (name to value)).toString()

/** The response of [StubError] with a replaced `Retry-After` header, for back-off scenarios. */
fun StubError.withRetryAfter(seconds: Int): StubResponse =
    response.copy(headers = response.headers + ("Retry-After" to seconds.toString()))
