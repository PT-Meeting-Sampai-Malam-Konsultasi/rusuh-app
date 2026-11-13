package com.rusuh.app.data.remote

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiClient.Companion.configure
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.remote.dto.BookingDto
import com.rusuh.app.data.remote.dto.EnterPaymentDto
import com.rusuh.app.data.storage.InMemoryTokenStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class PaymentApiServiceCoverageTest {
    @Test
    fun paymentRoutesUseBookingPathsAdmissionHeaderAndNoBody() =
        runTest {
            val tokenStore = InMemoryTokenStore().apply { setAdmissionToken("adm_test") }
            val requests = mutableListOf<HttpRequestData>()
            val service =
                PaymentApiService(
                    ApiClient(
                        BASE,
                        client =
                            HttpClient(
                                MockEngine { request ->
                                    requests += request
                                    respond(
                                        if (request.url.encodedPath.endsWith("/payment")) PAYMENT else BOOKING,
                                        HttpStatusCode.OK,
                                        headersOf(HttpHeaders.ContentType, "application/json"),
                                    )
                                },
                            ) { configure(BASE) },
                    ),
                    tokenStore,
                )

            assertIs<ApiResult.Success<EnterPaymentDto>>(service.startSnap("bk_test"))
            assertIs<ApiResult.Success<BookingDto>>(service.checkStatus("bk_test"))
            assertIs<ApiResult.Success<BookingDto>>(service.getBooking("bk_test"))
            assertEquals(
                listOf(
                    "/v1/core/bookings/bk_test/payment",
                    "/v1/core/bookings/bk_test/check-status",
                    "/v1/core/bookings/bk_test",
                ),
                requests.map { it.url.encodedPath },
            )
            requests.take(2).forEach {
                assertEquals("adm_test", it.headers["X-Admission-Token"])
            }
            requests.forEach {
                assertNull(it.headers[HttpHeaders.ContentType])
                assertNull(it.headers[HttpHeaders.ContentLength])
            }
        }

    @Test
    fun missingAdmissionTokenDoesNotCallNetwork() =
        runTest {
            var called = false
            val service =
                PaymentApiService(
                    ApiClient(
                        BASE,
                        client =
                            HttpClient(
                                MockEngine {
                                    called = true
                                    respond(BOOKING)
                                },
                            ) { configure(BASE) },
                    ),
                    InMemoryTokenStore(),
                )
            assertEquals(403, assertIs<ApiResult.Error>(service.startSnap("bk_test")).code)
            assertFalse(called)
        }

    private companion object {
        const val BASE = "https://api.test/v1"
        const val PAYMENT =
            """
            {"bookingId":"bk_test","snapToken":"token","paymentExpiresAt":"2026-11-14T10:10:00Z",
             "redirectUrl":"https://pay.example.test/snap","serverNow":"2026-11-14T10:00:00Z"}
            """
        const val BOOKING =
            """
            {"bookingId":"bk_test","eventId":"evt","status":"PAYMENT_PENDING",
             "price":{"lines":[],"ticketSubtotal":{"amount":1},"adminFee":{"amount":0},"total":{"amount":1}},
             "createdAt":"2026-11-14T10:00:00Z","serverNow":"2026-11-14T10:00:00Z"}
            """
    }
}
