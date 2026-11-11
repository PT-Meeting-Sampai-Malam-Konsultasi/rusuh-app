package com.rusuh.app.data.network

import com.rusuh.app.data.network.ApiClient.Companion.configure
import com.rusuh.app.domain.model.ErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiClientTest {
    @Serializable
    private data class Pong(
        @SerialName("message") val message: String = "",
        @SerialName("count") val count: Int = 0,
        @SerialName("note") val note: String? = null,
    )

    @Serializable
    private data class Echo(
        @SerialName("name") val name: String = "",
    )

    private val problemHeaders = headersOf(HttpHeaders.ContentType, "application/problem+json")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun apiClient(
        timeoutMs: Long = ApiClient.REQUEST_TIMEOUT_MS,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ApiClient =
        ApiClient(
            baseUrl = BASE_URL,
            client = HttpClient(MockEngine(handler)) { configure(BASE_URL, requestTimeoutMs = timeoutMs) },
        )

    private fun problemBody(
        status: Int,
        code: String,
        extra: String = "",
    ) = """{"type":"https://api.rusuh.id/problems/x","title":"Judul","status":$status,"code":"$code",
        |"detail":"Rincian","traceId":"trace-1"$extra}
        """.trimMargin()

    private suspend fun errorFor(
        status: HttpStatusCode,
        code: String,
        extra: String = "",
    ): ApiResult.Error {
        val client =
            apiClient { respond(problemBody(status.value, code, extra), status, problemHeaders) }
        val result = client.get<Pong>("/anything")
        return assertIs<ApiResult.Error>(result)
    }

    @Test
    fun `success body is the payload itself and keeps the status`() =
        runTest {
            val client =
                apiClient { respond("""{"message":"hi","count":2,"unknown":true}""", HttpStatusCode.OK, jsonHeaders) }

            val result = client.get<Pong>("/ping")

            assertEquals(ApiResult.Success(200, Pong("hi", 2)), result)
        }

    @Test
    fun `request resolves the path against the base url and sends accept json`() =
        runTest {
            var seen: HttpRequestData? = null
            val client =
                apiClient { request ->
                    seen = request
                    respond("""{}""", HttpStatusCode.OK, jsonHeaders)
                }

            client.get<Pong>("/events/42")

            assertEquals("https://api.test/v1/events/42", seen?.url.toString())
            assertEquals(HttpMethod.Get, seen?.method)
            assertTrue(seen?.headers?.get(HttpHeaders.Accept)?.contains("application/json") == true)
        }

    @Test
    fun `each helper uses its http method`() =
        runTest {
            val methods = mutableListOf<HttpMethod>()
            val client =
                apiClient { request ->
                    methods += request.method
                    respond("", HttpStatusCode.NoContent)
                }

            client.get<Unit>("a")
            client.post<Unit>("a")
            client.put<Unit>("a")
            client.patch<Unit>("a")
            client.delete<Unit>("a")

            assertEquals(
                listOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete),
                methods,
            )
        }

    @Test
    fun `post sends the json body`() =
        runTest {
            var sentBody = ""
            var sentType: String? = null
            val client =
                apiClient { request ->
                    sentBody = (request.body as TextContent).text
                    sentType = request.body.contentType?.toString()
                    respond("""{"name":"ok"}""", HttpStatusCode.Created, jsonHeaders)
                }

            val result = client.post<Echo>("echo") { jsonBody(Echo("Rusuh")) }

            assertEquals(ApiResult.Success(201, Echo("ok")), result)
            assertEquals("""{"name":"Rusuh"}""", sentBody)
            assertTrue(sentType?.startsWith("application/json") == true)
        }

    @Test
    fun `204 no content succeeds as Unit`() =
        runTest {
            val client = apiClient { respond("", HttpStatusCode.NoContent) }

            val result = client.delete<Unit>("/bookings/1")

            assertEquals(ApiResult.Success(204, Unit), result)
        }

    @Test
    fun `problem json 400 401 403 404 410 422 429 are parsed with their codes`() =
        runTest {
            val cases =
                listOf(
                    HttpStatusCode.BadRequest to ErrorCode.VALIDATION_FAILED,
                    HttpStatusCode.Unauthorized to ErrorCode.UNAUTHORIZED,
                    HttpStatusCode.Forbidden to ErrorCode.FORBIDDEN,
                    HttpStatusCode.NotFound to ErrorCode.NOT_FOUND,
                    HttpStatusCode.Gone to ErrorCode.EXPIRED,
                    HttpStatusCode.UnprocessableEntity to ErrorCode.NIK_INVALID,
                    HttpStatusCode.TooManyRequests to ErrorCode.RATE_LIMITED,
                )
            cases.forEach { (status, code) ->
                val error = errorFor(status, code.name)

                assertEquals(status.value, error.code)
                assertEquals(code, error.problem?.code)
                assertEquals(status.value, error.problem?.status)
                assertEquals("Judul", error.problem?.title)
                assertEquals("Rincian", error.problem?.detail)
                assertEquals("trace-1", error.problem?.traceId)
                assertEquals("https://api.rusuh.id/problems/x", error.problem?.type)
            }
        }

    @Test
    fun `409 keeps extra members in extras`() =
        runTest {
            val error =
                errorFor(
                    HttpStatusCode.Conflict,
                    "CATEGORY_SOLD_OUT",
                    extra = ""","categoryId":"cat-7","attemptsLeft":2,"nikMasked":"3171********0001"""",
                )

            assertEquals(409, error.code)
            assertEquals(ErrorCode.CATEGORY_SOLD_OUT, error.problem?.code)
            assertEquals(
                mapOf(
                    "categoryId" to JsonPrimitive("cat-7"),
                    "attemptsLeft" to JsonPrimitive(2),
                    "nikMasked" to JsonPrimitive("3171********0001"),
                ),
                error.problem?.extras,
            )
        }

    @Test
    fun `unknown error code falls back to UNKNOWN`() =
        runTest {
            val error = errorFor(HttpStatusCode.Conflict, "BRAND_NEW_CODE")

            assertEquals(ErrorCode.UNKNOWN, error.problem?.code)
            assertEquals(409, error.code)
        }

    @Test
    fun `5xx with a plain text body has no problem`() =
        runTest {
            val client =
                apiClient {
                    respond(
                        "upstream exploded",
                        HttpStatusCode.BadGateway,
                        headersOf(HttpHeaders.ContentType, "text/plain"),
                    )
                }

            assertEquals(ApiResult.Error(502, null), client.get<Pong>("/x"))
        }

    @Test
    fun `5xx with an empty body has no problem`() =
        runTest {
            val client = apiClient { respond("", HttpStatusCode.InternalServerError) }

            assertEquals(ApiResult.Error(500, null), client.get<Pong>("/x"))
        }

    @Test
    fun `error with a malformed problem body has no problem`() =
        runTest {
            val client = apiClient { respond("{not json", HttpStatusCode.ServiceUnavailable, problemHeaders) }

            assertEquals(ApiResult.Error(503, null), client.get<Pong>("/x"))
        }

    @Test
    fun `error with a json body that is not problem json is ignored`() =
        runTest {
            val client = apiClient { respond("""{"code":"NOT_FOUND"}""", HttpStatusCode.NotFound, jsonHeaders) }

            assertEquals(ApiResult.Error(404, null), client.get<Pong>("/x"))
        }

    @Test
    fun `request timeout becomes an exception result`() =
        runTest {
            val client =
                apiClient(timeoutMs = 50) {
                    delay(10_000)
                    respond("""{}""", HttpStatusCode.OK, jsonHeaders)
                }

            val result = client.get<Pong>("/slow")

            assertIs<HttpRequestTimeoutException>(assertIs<ApiResult.Failure>(result).throwable)
        }

    @Test
    fun `malformed success body becomes an exception result`() =
        runTest {
            val client = apiClient { respond("{not json", HttpStatusCode.OK, jsonHeaders) }

            val result = client.get<Pong>("/x")

            assertIs<ApiResult.Failure>(result)
        }

    @Test
    fun `body of the wrong shape becomes an exception result`() =
        runTest {
            val client = apiClient { respond("""[1,2,3]""", HttpStatusCode.OK, jsonHeaders) }

            assertIs<ApiResult.Failure>(client.get<Pong>("/x"))
        }

    @Test
    fun `null members decode to defaults`() =
        runTest {
            val client = apiClient { respond("""{"message":"m","note":null}""", HttpStatusCode.OK, jsonHeaders) }

            val data = assertIs<ApiResult.Success<Pong>>(client.get<Pong>("/x")).data

            assertEquals(Pong(message = "m"), data)
            assertNull(data.note)
        }

    @Test
    fun `cancellation thrown by the engine is rethrown not wrapped`() =
        runTest {
            val client = apiClient { throw CancellationException("cancelled") }

            assertFailsWith<CancellationException> { client.get<Pong>("/x") }
        }

    @Test
    fun `cancelling the caller cancels the call without producing a result`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val client =
                apiClient {
                    started.complete(Unit)
                    awaitCancellation()
                }
            val call = async(start = CoroutineStart.UNDISPATCHED) { client.get<Pong>("/x") }

            started.await()
            call.cancel()

            assertFailsWith<CancellationException> { call.await() }
        }

    @Test
    fun `429 and 503 carry the Retry-After seconds header`() =
        runTest {
            listOf(
                HttpStatusCode.TooManyRequests to "RATE_LIMITED",
                HttpStatusCode.ServiceUnavailable to "SERVICE_UNAVAILABLE",
            )
                .forEach { (status, code) ->
                    val client =
                        apiClient {
                            respond(
                                problemBody(status.value, code),
                                status,
                                headersOf(
                                    HttpHeaders.ContentType to listOf("application/problem+json"),
                                    HttpHeaders.RetryAfter to listOf("30"),
                                ),
                            )
                        }

                    val error = assertIs<ApiResult.Error>(client.get<Pong>("/x"))

                    assertEquals(30, error.retryAfterSeconds)
                    assertEquals(status.value, error.code)
                }
        }

    @Test
    fun `Retry-After in HTTP-date form or absent is ignored`() =
        runTest {
            val dated =
                apiClient {
                    respond(
                        "",
                        HttpStatusCode.TooManyRequests,
                        headersOf(HttpHeaders.RetryAfter, "Wed, 21 Oct 2026 07:28:00 GMT"),
                    )
                }
            val absent = apiClient { respond("", HttpStatusCode.TooManyRequests) }

            assertNull(assertIs<ApiResult.Error>(dated.get<Pong>("/x")).retryAfterSeconds)
            assertNull(assertIs<ApiResult.Error>(absent.get<Pong>("/x")).retryAfterSeconds)
        }

    @Test
    fun `engine io failure becomes a failure result`() =
        runTest {
            val client = apiClient { throw IOException("offline") }

            val failure = assertIs<ApiResult.Failure>(client.get<Pong>("/x"))

            assertIs<IOException>(failure.throwable)
        }

    @Test
    fun `200 with an empty body for a non Unit type becomes a failure`() =
        runTest {
            val client = apiClient { respond("", HttpStatusCode.OK, jsonHeaders) }

            assertIs<ApiResult.Failure>(client.get<Pong>("/x"))
        }

    @Test
    fun `problem json content type with charset or upper case is still parsed`() =
        runTest {
            listOf("application/problem+json; charset=UTF-8", "Application/Problem+JSON").forEach { type ->
                val client =
                    apiClient {
                        respond(
                            problemBody(404, "NOT_FOUND"),
                            HttpStatusCode.NotFound,
                            headersOf(HttpHeaders.ContentType, type),
                        )
                    }

                val error = assertIs<ApiResult.Error>(client.get<Pong>("/x"))

                assertEquals(ErrorCode.NOT_FOUND, error.problem?.code, type)
            }
        }

    @Test
    fun `3xx and 304 responses are errors`() =
        runTest {
            listOf(HttpStatusCode.NotModified, HttpStatusCode.MultipleChoices).forEach { status ->
                val client = apiClient { respond("", status) }

                assertEquals(ApiResult.Error(status.value, null, null), client.get<Pong>("/x"))
            }
        }

    @Test
    fun `problem body that is not a json object has no problem`() =
        runTest {
            val client = apiClient { respond("[1,2]", HttpStatusCode.BadRequest, problemHeaders) }

            assertEquals(ApiResult.Error(400, null, null), client.get<Pong>("/x"))
        }

    @Test
    fun `oversized problem body is not parsed`() =
        runTest {
            val padding = "a".repeat(ApiClient.MAX_PROBLEM_BYTES + 10)
            val client =
                apiClient {
                    respond(
                        problemBody(409, "NO_STOCK", ",\"pad\":\"$padding\""),
                        HttpStatusCode.Conflict,
                        problemHeaders,
                    )
                }

            assertEquals(ApiResult.Error(409, null, null), client.get<Pong>("/x"))
        }

    @Test
    fun `null members are omitted from the request body`() =
        runTest {
            var sentBody = ""
            val client =
                apiClient { request ->
                    sentBody = (request.body as TextContent).text
                    respond("""{}""", HttpStatusCode.OK, jsonHeaders)
                }

            client.post<Pong>("x") { jsonBody(Pong(message = "m", note = null)) }

            assertEquals("""{"message":"m","count":0}""", sentBody)
        }

    @Test
    fun `getBytes returns raw bytes and forces no json accept`() =
        runTest {
            var accept: String? = null
            val bytes = byteArrayOf(0x25, 0x50, 0x44, 0x46)
            val client =
                apiClient { request ->
                    accept = request.headers[HttpHeaders.Accept]
                    respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/pdf"))
                }

            val result = assertIs<ApiResult.Success<ByteArray>>(client.getBytes("/tickets/1/pdf"))

            assertEquals(bytes.toList(), result.data.toList())
            assertEquals("*/*", accept)
        }

    @Test
    fun `getBytes maps an error status to Error`() =
        runTest {
            val client = apiClient { respond(problemBody(404, "NOT_FOUND"), HttpStatusCode.NotFound, problemHeaders) }

            val error = assertIs<ApiResult.Error>(client.getBytes("/tickets/1/pdf"))

            assertEquals(ErrorCode.NOT_FOUND, error.problem?.code)
        }

    @Test
    fun `idempotencyKey sets the Idempotency-Key header`() =
        runTest {
            var key: String? = null
            val client =
                apiClient { request ->
                    key = request.headers["Idempotency-Key"]
                    respond("""{}""", HttpStatusCode.OK, jsonHeaders)
                }

            client.post<Pong>("bookings") { idempotencyKey("abc-123") }

            assertEquals("abc-123", key)
        }

    private companion object {
        const val BASE_URL = "https://api.test/v1"
    }
}
