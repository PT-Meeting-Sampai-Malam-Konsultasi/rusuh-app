package com.rusuh.app.data.remote

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiClient.Companion.configure
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.remote.dto.CreateBookingRequestDto
import com.rusuh.app.data.remote.dto.ValidateAttendeeRequestDto
import com.rusuh.app.data.storage.InMemoryTokenStore
import com.rusuh.app.domain.model.ErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookingApiServiceTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun apiClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): ApiClient =
        ApiClient(
            baseUrl = BASE_URL,
            client = HttpClient(MockEngine(handler)) { configure(BASE_URL) },
        )

    private fun categoriesJson(): String =
        """
        {
            "eventId": "evt_1",
            "categories": [],
            "maxTicketsPerOrder": 3,
            "maxTickets": 3,
            "adminFee": {"amount": 10000, "currency": "IDR"},
            "expiresAt": "2026-11-14T10:45:00Z",
            "serverNow": "2026-11-14T10:40:00Z"
        }
        """.trimIndent()

    private fun validateResponseJson(): String =
        """
        {
            "validationToken": "av_123",
            "nikMasked": "************0004",
            "expiresAt": "2026-11-14T10:45:00Z"
        }
        """.trimIndent()

    private fun bookingJson(): String =
        """
        {
            "bookingId": "bk_1",
            "eventId": "evt_1",
            "status": "SELECTION",
            "price": {
                "lines": [],
                "ticketSubtotal": {"amount": 0, "currency": "IDR"},
                "adminFee": {"amount": 10000, "currency": "IDR"},
                "total": {"amount": 10000, "currency": "IDR"}
            },
            "orderCode": "RSH-26-000001",
            "attendees": [],
            "expiresAt": "2026-11-14T10:45:00Z",
            "refundRequired": false,
            "tickets": [],
            "paidAt": null,
            "paymentMethod": null,
            "createdAt": "2026-11-14T10:40:00Z",
            "serverNow": "2026-11-14T10:40:00Z"
        }
        """.trimIndent()

    // --- X-Admission-Token header tests ---

    @Test
    fun getCategoriesSendsAdmissionTokenHeader() =
        runTest {
            var sentAdmissionToken: String? = null
            val tokens = InMemoryTokenStore().apply { setAdmissionToken("adm_test_token") }
            val api =
                apiClient { request ->
                    sentAdmissionToken = request.headers[ADMISSION_TOKEN_HEADER]
                    respond(categoriesJson(), HttpStatusCode.OK, jsonHeaders)
                }
            val service = BookingApiService(api, tokens)

            val result = service.getCategories("evt_1")

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("adm_test_token", sentAdmissionToken)
        }

    @Test
    fun getCategoriesEncodesEventIdAsPathSegment() =
        runTest {
            var requestPath = ""
            val tokens = InMemoryTokenStore().apply { setAdmissionToken("adm_test_token") }
            val api =
                apiClient { request ->
                    requestPath = request.url.encodedPath
                    respond(categoriesJson(), HttpStatusCode.OK, jsonHeaders)
                }

            BookingApiService(api, tokens).getCategories("event/one?two")

            assertEquals("/v1/core/events/event%2Fone%3Ftwo/categories", requestPath)
        }

    @Test
    fun getCategoriesWithoutAdmissionTokenReturnsAdmissionInvalidError() =
        runTest {
            val tokens = InMemoryTokenStore() // No admission token set
            var requestMade = false
            val api =
                apiClient {
                    requestMade = true
                    respond(categoriesJson(), HttpStatusCode.OK, jsonHeaders)
                }
            val service = BookingApiService(api, tokens)

            val result = service.getCategories("evt_1")

            // Should NOT make a network request
            assertTrue(!requestMade, "Should not make a request when token is missing")
            // Should return ADMISSION_INVALID error
            val error = assertIs<ApiResult.Error>(result)
            assertEquals(403, error.code)
            assertEquals(ErrorCode.ADMISSION_INVALID, error.problem?.code)
        }

    @Test
    fun validateAttendeeSendsAdmissionTokenHeader() =
        runTest {
            var sentAdmissionToken: String? = null
            val tokens = InMemoryTokenStore().apply { setAdmissionToken("adm_validate_token") }
            val api =
                apiClient { request ->
                    sentAdmissionToken = request.headers[ADMISSION_TOKEN_HEADER]
                    respond(validateResponseJson(), HttpStatusCode.OK, jsonHeaders)
                }
            val service = BookingApiService(api, tokens)

            val result =
                service.validateAttendee(
                    "evt_1",
                    ValidateAttendeeRequestDto(fieldIndex = 0, fullName = "Test", nik = "1234567890123456"),
                )

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("adm_validate_token", sentAdmissionToken)
        }

    @Test
    fun validateAttendeeWithoutAdmissionTokenReturnsAdmissionInvalidError() =
        runTest {
            val tokens = InMemoryTokenStore()
            var requestMade = false
            val api =
                apiClient {
                    requestMade = true
                    respond(validateResponseJson(), HttpStatusCode.OK, jsonHeaders)
                }
            val service = BookingApiService(api, tokens)

            val result =
                service.validateAttendee(
                    "evt_1",
                    ValidateAttendeeRequestDto(fieldIndex = 0, fullName = "Test", nik = "1234567890123456"),
                )

            assertTrue(!requestMade)
            val error = assertIs<ApiResult.Error>(result)
            assertEquals(403, error.code)
            assertEquals(ErrorCode.ADMISSION_INVALID, error.problem?.code)
        }

    @Test
    fun createBookingSendsAdmissionTokenAndIdempotencyKeyHeaders() =
        runTest {
            var sentAdmissionToken: String? = null
            var sentIdempotencyKey: String? = null
            val tokens = InMemoryTokenStore().apply { setAdmissionToken("adm_booking_token") }
            val api =
                apiClient { request ->
                    sentAdmissionToken = request.headers[ADMISSION_TOKEN_HEADER]
                    sentIdempotencyKey = request.headers["Idempotency-Key"]
                    respond(bookingJson(), HttpStatusCode.Created, jsonHeaders)
                }
            val service = BookingApiService(api, tokens)

            val result =
                service.createBooking(
                    CreateBookingRequestDto(eventId = "evt_1", tickets = emptyList()),
                    idempotencyKey = "key-123",
                )

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("adm_booking_token", sentAdmissionToken)
            assertEquals("key-123", sentIdempotencyKey)
        }

    @Test
    fun createBookingWithoutAdmissionTokenReturnsAdmissionInvalidError() =
        runTest {
            val tokens = InMemoryTokenStore()
            var requestMade = false
            val api =
                apiClient {
                    requestMade = true
                    respond(bookingJson(), HttpStatusCode.Created, jsonHeaders)
                }
            val service = BookingApiService(api, tokens)

            val result =
                service.createBooking(
                    CreateBookingRequestDto(eventId = "evt_1", tickets = emptyList()),
                    idempotencyKey = "key-123",
                )

            assertTrue(!requestMade)
            val error = assertIs<ApiResult.Error>(result)
            assertEquals(403, error.code)
            assertEquals(ErrorCode.ADMISSION_INVALID, error.problem?.code)
        }

    // --- Endpoints that do NOT require admission token ---

    @Test
    fun getBookingDoesNotRequireAdmissionToken() =
        runTest {
            var sentAdmissionToken: String? = null
            val tokens = InMemoryTokenStore() // No admission token
            val api =
                apiClient { request ->
                    sentAdmissionToken = request.headers[ADMISSION_TOKEN_HEADER]
                    respond(bookingJson(), HttpStatusCode.OK, jsonHeaders)
                }
            val service = BookingApiService(api, tokens)

            val result = service.getBooking("bk_1")

            assertIs<ApiResult.Success<*>>(result)
            assertNull(sentAdmissionToken, "getBooking should not send admission token")
        }

    @Test
    fun cancelBookingDoesNotRequireAdmissionToken() =
        runTest {
            var sentAdmissionToken: String? = null
            var path = ""
            var method = ""
            val tokens = InMemoryTokenStore()
            val api =
                apiClient { request ->
                    sentAdmissionToken = request.headers[ADMISSION_TOKEN_HEADER]
                    path = request.url.encodedPath
                    method = request.method.value
                    respond(bookingJson(), HttpStatusCode.OK, jsonHeaders)
                }
            val service = BookingApiService(api, tokens)

            val result = service.cancelBooking("bk_1")

            assertIs<ApiResult.Success<*>>(result)
            assertNull(sentAdmissionToken)
            assertEquals("/v1/core/bookings/bk_1/cancel", path)
            assertEquals("POST", method)
        }

    @Test
    fun cancelBookingPreservesNotCancellableProblem() =
        runTest {
            val api =
                apiClient {
                    respond(
                        """{"type":"https://api.rusuh.id/problems/not-cancellable",""" +
                            """"title":"Not cancellable","status":409,"code":"NOT_CANCELLABLE"}""",
                        HttpStatusCode.Conflict,
                        headersOf(HttpHeaders.ContentType, "application/problem+json"),
                    )
                }
            val service = BookingApiService(api, InMemoryTokenStore())

            val result = service.cancelBooking("bk_1")

            val error = assertIs<ApiResult.Error>(result)
            assertEquals(409, error.code)
            assertEquals(ErrorCode.NOT_CANCELLABLE, error.problem?.code)
        }

    @Test
    fun getActiveBookingsDoesNotRequireAdmissionToken() =
        runTest {
            var sentAdmissionToken: String? = null
            val tokens = InMemoryTokenStore()
            val api =
                apiClient { request ->
                    sentAdmissionToken = request.headers[ADMISSION_TOKEN_HEADER]
                    respond("""{"items": []}""", HttpStatusCode.OK, jsonHeaders)
                }
            val service = BookingApiService(api, tokens)

            val result = service.getActiveBookings()

            assertIs<ApiResult.Success<*>>(result)
            assertNull(sentAdmissionToken)
        }

    private companion object {
        const val BASE_URL = "https://api.test/v1"
        const val ADMISSION_TOKEN_HEADER = "X-Admission-Token"
    }
}
