@file:Suppress(
    "WildcardImport",
    "MaxLineLength",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package com.rusuh.app.data.repository

import com.rusuh.app.data.remote.PaymentApiService
import com.rusuh.app.data.storage.InMemoryTokenStore
import com.rusuh.app.domain.model.*
import com.rusuh.app.domain.repository.AccountEmailStore
import io.ktor.http.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PaymentRepositoryImplCoverageTest {
    @Test fun loadsMapsAndPropagatesHttpErrors() =
        runTest {
            val backend =
                FakeBackend().apply {
                    on("GET /core/bookings/bk_test", Reply.ok(BOOKING))
                    on("GET /core/bookings/bk_missing", Reply.notFound())
                }
            val repository = repository(backend.api)
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
                    on("POST /core/bookings/bk_test/payment", Reply.ok(SNAP))
                    on("GET /core/bookings/bk_test", Reply.ok(PAID))
                }
            val repository = PaymentRepositoryImpl(service(backend.api), emails, pollIntervalMs = 0, jitterMs = 0)
            val snap =
                assertIs<DomainResult.Success<SnapTransaction>>(
                    repository.startSnap("bk_test"),
                ).data
            assertEquals("https://pay.example.test/snap", snap.redirectUrl)
            val statuses = repository.observePaymentStatus("bk_test").toList()
            assertEquals(BookingStatus.PAID, statuses.single().bookingStatus)
        }

    @Test fun offlineLoadMapsToNetworkError() =
        runTest {
            val backend = FakeBackend().apply { on("GET /core/bookings/bk_test", Reply.offline()) }
            assertEquals(
                DomainResult.NetworkError,
                repository(backend.api).loadPayment("bk_test"),
            )
        }

    @Test fun paymentConflictKeepsContractErrorCodeAndExtras() =
        runTest {
            val backend =
                FakeBackend().apply {
                    on("POST /core/bookings/bk_conflict/payment", Reply.problem(409, "CATEGORY_SOLD_OUT"))
                }
            val result = assertIs<DomainResult.Error>(repository(backend.api).startSnap("bk_conflict"))
            assertEquals(409, result.code)
            assertEquals(ErrorCode.CATEGORY_SOLD_OUT, result.errorCode)
        }

    private suspend fun service(api: com.rusuh.app.data.network.ApiClient): PaymentApiService =
        PaymentApiService(api, InMemoryTokenStore().apply { setAdmissionToken("adm") })

    private suspend fun repository(api: com.rusuh.app.data.network.ApiClient) =
        PaymentRepositoryImpl(
            service(api),
            emails,
        )

    private val emails =
        object : AccountEmailStore {
            override suspend fun email() = "rina@example.com"

            override suspend fun saveEmail(
                userId: String,
                email: String,
            ) = Unit

            override suspend fun clearEmail() = Unit
        }

    private companion object {
        const val BOOKING =
            """
            {"bookingId":"bk_test","eventId":"evt","status":"PAYMENT_PENDING","orderCode":"RSH-TEST",
             "attendees":[],"price":{"lines":[],"ticketSubtotal":{"amount":1},"adminFee":{"amount":0},"total":{"amount":1}},
             "expiresAt":"2026-11-14T10:10:00Z","createdAt":"2026-11-14T10:00:00Z","serverNow":"2026-11-14T10:00:00Z"}
            """
        const val PAID =
            """
            {"bookingId":"bk_test","eventId":"evt","status":"PAID","orderCode":"RSH-TEST","attendees":[],
             "price":{"lines":[],"ticketSubtotal":{"amount":1},"adminFee":{"amount":0},"total":{"amount":1}},
             "expiresAt":null,"createdAt":"2026-11-14T10:00:00Z","serverNow":"2026-11-14T10:00:00Z"}
            """
        const val SNAP =
            """
            {"bookingId":"bk_test","snapToken":"token_test","paymentExpiresAt":"2026-11-14T10:10:00Z",
             "redirectUrl":"https://pay.example.test/snap","serverNow":"2026-11-14T10:00:00Z"}
            """
    }
}
