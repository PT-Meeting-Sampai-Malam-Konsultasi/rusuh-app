@file:Suppress(
    "WildcardImport",
    "MaxLineLength",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package com.rusuh.app.domain.usecase

import com.rusuh.app.domain.model.*
import com.rusuh.app.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.*

class PaymentUseCasesCoverageTest {
    private class Repo : PaymentRepository {
        var booking = ""
        var key = ""
        var method: PaymentMethod? = null
        val summary =
            DomainResult.Success(
                PaymentSummary(
                    "bk_test", "RSH-TEST", null, null, null, null, emptyList(), null,
                    PriceBreakdown(
                        emptyList(),
                        1,
                        0,
                        null,
                        1,
                    ),
                    null, Instant.parse("2026-11-14T10:10:00Z"), Instant.parse("2026-11-14T10:00:00Z"), "client",
                ),
            )
        val transaction =
            SnapTransaction(
                1,
                "order",
                PaymentMethod.QRIS,
                "token",
                "https://pay.example.test/snap",
                Instant.parse("2026-11-14T10:10:00Z"),
            )

        override suspend fun loadPayment(bookingId: String): DomainResult<PaymentSummary> {
            booking = bookingId
            return summary
        }

        override suspend fun startSnap(
            bookingId: String,
            idempotencyKey: String,
            method: PaymentMethod?,
        ): DomainResult<SnapTransaction> {
            booking = bookingId
            key = idempotencyKey
            this.method = method
            return DomainResult.Success(transaction)
        }

        override fun observePaymentStatus(bookingId: String) =
            flowOf(
                PaymentStatusResult(
                    BookingStatus.PAID,
                    PaymentStatus.SETTLED,
                    null,
                    Instant.parse("2026-11-14T10:00:00Z"),
                ),
            )
    }

    @Test fun loadAndObserveDelegateToRepository() =
        runTest {
            val repo = Repo()
            assertEquals(repo.summary, LoadPaymentUseCase(repo)("bk_test"))
            assertEquals(BookingStatus.PAID, ObservePaymentStatusUseCase(repo)("bk_test").first().bookingStatus)
        }

    @Test fun startReturnsGeneratedKeyAndRetryReusesProvidedKey() =
        runTest {
            val repo = Repo()
            val useCase = StartSnapPaymentUseCase(repo)
            val result = useCase.invoke("bk_test", PaymentMethod.QRIS)
            assertIs<DomainResult.Success<SnapTransaction>>(result.result)
            assertTrue(result.idempotencyKey.isNotBlank())
            assertEquals(result.idempotencyKey, repo.key)
            assertEquals(PaymentMethod.QRIS, repo.method)
            assertEquals(DomainResult.Success(repo.transaction), useCase.invokeWithKey("bk_test", "stable-key"))
            assertEquals("stable-key", repo.key)
        }
}
