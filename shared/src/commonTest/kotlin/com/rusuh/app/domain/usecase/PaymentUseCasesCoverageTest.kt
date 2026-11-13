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
                    Instant.parse("2026-11-14T10:10:00Z"), Instant.parse("2026-11-14T10:00:00Z"),
                ),
            )
        val transaction =
            SnapTransaction(
                "bk_test",
                "token",
                "https://pay.example.test/snap",
                Instant.parse("2026-11-14T10:10:00Z"),
            )

        override suspend fun loadPayment(bookingId: String): DomainResult<PaymentSummary> {
            booking = bookingId
            return summary
        }

        override suspend fun startSnap(bookingId: String): DomainResult<SnapTransaction> {
            booking = bookingId
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

    @Test fun startDelegatesWithoutClientIdempotencyKey() =
        runTest {
            val repo = Repo()
            val useCase = StartSnapPaymentUseCase(repo)
            assertEquals(DomainResult.Success(repo.transaction), useCase("bk_test"))
            assertEquals("bk_test", repo.booking)
        }
}
