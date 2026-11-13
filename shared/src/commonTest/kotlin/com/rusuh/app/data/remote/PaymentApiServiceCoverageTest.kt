@file:Suppress(
    "WildcardImport",
    "MaxLineLength",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package com.rusuh.app.data.remote

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiClient.Companion.configure
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.remote.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PaymentApiServiceCoverageTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun service(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        PaymentApiService(
            ApiClient(
                BASE,
                client =
                    HttpClient(
                        MockEngine(handler),
                    ) {
                        configure(BASE)
                    },
            ),
        )

    @Test fun readsPaymentAndStatusPaths() =
        runTest {
            val paths = mutableListOf<String>()
            val api =
                service {
                        request ->
                    paths += request.url.encodedPath
                    respond(
                        if (request.url.encodedPath.endsWith("/status")) STATUS else SUMMARY,
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }
            assertIs<ApiResult.Success<PaymentViewDto>>(api.getPayment("bk_test"))
            assertIs<ApiResult.Success<PaymentStatusDto>>(api.getPaymentStatus("bk_test"))
            assertEquals(listOf("/v1/core/payments/bk_test", "/v1/core/payments/bk_test/status"), paths)
        }

    @Test fun startsSnapWithOptionalMethodAndIdempotencyHeader() =
        runTest {
            val bodies = mutableListOf<String>()
            val keys = mutableListOf<String>()
            val api =
                service {
                        request ->
                    bodies += (request.body as? io.ktor.client.request.HttpRequestBuilder)?.toString().orEmpty()
                    keys += request.headers["Idempotency-Key"].orEmpty()
                    respond(SNAP, HttpStatusCode.Created, jsonHeaders)
                }
            assertIs<ApiResult.Success<SnapTransactionDto>>(api.startSnap("bk_test", "key-a"))
            assertIs<ApiResult.Success<SnapTransactionDto>>(api.startSnap("bk_test", "key-b", "qris"))
            assertEquals(listOf("key-a", "key-b"), keys)
            assertEquals(2, bodies.size)
        }

    @Test fun mapsProviderAndNotFoundErrors() =
        runTest {
            val api =
                service { request ->
                    val code = if (request.url.encodedPath.endsWith("/snap")) 503 else 404
                    respond(
                        """{"type":"about:blank","title":"Error","status":$code,"code":"PAYMENT_PROVIDER_BUSY"}""",
                        HttpStatusCode.fromValue(code),
                        headersOf(HttpHeaders.ContentType, "application/problem+json"),
                    )
                }
            assertEquals(503, assertIs<ApiResult.Error>(api.startSnap("bk_test", "key")).code)
            assertEquals(404, assertIs<ApiResult.Error>(api.getPayment("bk_test")).code)
        }

    private companion object {
        const val BASE = "https://api.test/v1"
        const val SUMMARY = """{"bookingId":"bk_test","orderCode":"RSH-TEST","price":{"lines":[],"ticketSubtotal":{"amount":1},"adminFee":{"amount":0},"total":{"amount":1}},"expiresAt":"2026-11-14T10:10:00Z","serverNow":"2026-11-14T10:00:00Z","csrfToken":"csrf","snapClientKey":"client"}"""
        const val SNAP = """{"attemptId":1,"orderId":"order_test","method":"qris","snapToken":"token_test","redirectUrl":"https://pay.example.test/snap","gatewayExpiresAt":"2026-11-14T10:10:00Z"}"""
        const val STATUS = """{"bookingStatus":"PAID","paymentStatus":"SETTLED","serverNow":"2026-11-14T10:00:00Z"}"""
    }
}
