@file:Suppress(
    "WildcardImport",
    "MaxLineLength",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package com.rusuh.app.data.repository

import com.rusuh.app.data.remote.PaymentApiService
import com.rusuh.app.domain.model.*
import io.ktor.http.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PaymentRepositoryImplCoverageTest {
    @Test fun loadsMapsAndPropagatesHttpErrors() =
        runTest {
            val backend =
                FakeBackend().apply {
                    on("GET /core/payments/bk_test", Reply.ok(PAYMENT))
                    on("GET /core/payments/bk_missing", Reply.notFound())
                }
            val repository = PaymentRepositoryImpl(PaymentApiService(backend.api))
            val result = repository.loadPayment("bk_test")
            assertIs<DomainResult.Success<PaymentSummary>>(result, backend.seen.firstOrNull()?.line.orEmpty())
            val loaded = (result as DomainResult.Success).data
            assertEquals("RSH-TEST", loaded.orderCode)
            assertEquals(404, assertIs<DomainResult.Error>(repository.loadPayment("bk_missing")).code)
        }

    @Test fun startsSnapAndMapsStatusFlow() =
        runTest {
            val backend =
                FakeBackend().apply {
                    on("POST /core/payments/bk_test/snap", Reply.created(SNAP))
                    on("GET /core/payments/bk_test/status", Reply.ok(STATUS))
                }
            val repository = PaymentRepositoryImpl(PaymentApiService(backend.api), pollIntervalMs = 0, jitterMs = 0)
            val snap =
                assertIs<DomainResult.Success<SnapTransaction>>(
                    repository.startSnap("bk_test", "key", PaymentMethod.QRIS),
                ).data
            assertEquals(PaymentMethod.QRIS, snap.method)
            val statuses = repository.observePaymentStatus("bk_test").toList()
            assertEquals(BookingStatus.PAID, statuses.single().bookingStatus)
        }

    @Test fun offlineLoadMapsToNetworkError() =
        runTest {
            val backend = FakeBackend().apply { on("GET /core/payments/bk_test", Reply.offline()) }
            assertEquals(
                DomainResult.NetworkError,
                PaymentRepositoryImpl(PaymentApiService(backend.api)).loadPayment("bk_test"),
            )
        }

    private companion object {
        const val PAYMENT = """{"bookingId":"bk_test","orderCode":"RSH-TEST","price":{"lines":[],"ticketSubtotal":{"amount":1},"adminFee":{"amount":0},"total":{"amount":1}},"expiresAt":"2026-11-14T10:10:00Z","serverNow":"2026-11-14T10:00:00Z","csrfToken":"csrf","snapClientKey":"client"}"""
        const val SNAP = """{"attemptId":1,"orderId":"order_test","method":"qris","snapToken":"token_test","redirectUrl":"https://pay.example.test/snap","gatewayExpiresAt":"2026-11-14T10:10:00Z"}"""
        const val STATUS = """{"bookingStatus":"PAID","paymentStatus":"SETTLED","serverNow":"2026-11-14T10:00:00Z"}"""
    }
}
