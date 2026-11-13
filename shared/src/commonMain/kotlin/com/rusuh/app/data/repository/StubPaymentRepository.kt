package com.rusuh.app.data.repository

import com.rusuh.app.data.stub.StubClock
import com.rusuh.app.domain.model.AttendeeInfo
import com.rusuh.app.domain.model.BookingStatus
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.PaymentMethod
import com.rusuh.app.domain.model.PaymentStatus
import com.rusuh.app.domain.model.PaymentStatusResult
import com.rusuh.app.domain.model.PaymentSummary
import com.rusuh.app.domain.model.PriceBreakdown
import com.rusuh.app.domain.model.PriceLine
import com.rusuh.app.domain.model.SnapTransaction
import com.rusuh.app.domain.repository.PaymentRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Stub implementation of [PaymentRepository] for offline demo and testing.
 * Returns hardcoded payment data matching the OpenAPI examples (synthetic data only).
 *
 * The polling flow simulates a payment flow: first 2-3 polls return PENDING, then one returns SETTLED.
 */
class StubPaymentRepository(
    private val clock: StubClock,
) : PaymentRepository {
    private fun nowInstant(): Instant = Instant.fromEpochMilliseconds(clock.nowMillis())

    override suspend fun loadPayment(bookingId: String): DomainResult<PaymentSummary> =
        DomainResult.Success(stubPaymentSummary(bookingId, nowInstant()))

    override suspend fun startSnap(
        bookingId: String,
        idempotencyKey: String,
        method: PaymentMethod?,
    ): DomainResult<SnapTransaction> = DomainResult.Success(stubSnapTransaction(bookingId, nowInstant(), method))

    override fun observePaymentStatus(bookingId: String): Flow<PaymentStatusResult> =
        flow {
            // Simulate polling: first few polls return PENDING, then SETTLED
            var pollCount = 0
            val maxPendingPolls = 3

            while (true) {
                pollCount++
                val now = nowInstant()
                val isFinal = pollCount > maxPendingPolls
                val status =
                    PaymentStatusResult(
                        bookingStatus = if (isFinal) BookingStatus.PAID else BookingStatus.PAYMENT_PENDING,
                        paymentStatus = if (isFinal) PaymentStatus.SETTLED else PaymentStatus.PENDING,
                        returnUrl = if (isFinal) "https://app.rusuh.id/payment-result?bookingId=$bookingId" else null,
                        serverNow = now,
                    )
                emit(status)
                if (isFinal) break
                delay(1.seconds) // Faster for stub mode
            }
        }
}

/** Stub payment summary matching the OpenAPI example (synthetic data). */
private fun stubPaymentSummary(
    bookingId: String,
    now: Instant,
): PaymentSummary =
    PaymentSummary(
        bookingId = bookingId,
        orderCode = "RSH-26-084731",
        eventName = "Samudra",
        eventStartsAt = Instant.parse("2026-12-12T12:00:00Z"),
        venueShort = "GBK",
        timezone = "Asia/Jakarta",
        attendees =
            listOf(
                AttendeeInfo(
                    categoryId = "cat_festival_b",
                    categoryName = "Festival B",
                    fullName = "Rina Wulandari",
                    nikMasked = "3174 •••• •••• 0004",
                ),
                AttendeeInfo(
                    categoryId = "cat_festival_b",
                    categoryName = "Festival B",
                    fullName = "Dimas Pratama",
                    nikMasked = "3275 •••• •••• 0019",
                ),
            ),
        customerEmail = "rina.wulandari@example.com",
        price =
            PriceBreakdown(
                lines =
                    listOf(
                        PriceLine(
                            categoryId = "cat_festival_b",
                            name = "Festival B",
                            quantity = 2,
                            unitPrice = 1_500_000L,
                            lineTotal = 3_000_000L,
                        ),
                    ),
                ticketSubtotal = 3_000_000L,
                adminFee = 15_000L,
                tax = null,
                total = 3_015_000L,
            ),
        currentSnap = null,
        expiresAt = now + 10.minutes,
        serverNow = now,
        snapClientKey = "SB-Mid-client-rusuhDemoKey",
    )

/** Stub Snap transaction matching the OpenAPI example (synthetic data). */
private fun stubSnapTransaction(
    bookingId: String,
    now: Instant,
    method: PaymentMethod?,
): SnapTransaction =
    SnapTransaction(
        attemptId = 1,
        orderId = "$bookingId-1",
        method = method,
        snapToken = "66e4fa55-fdac-4ef9-91b5-733b97d1b862",
        redirectUrl = "https://app.sandbox.midtrans.com/snap/v4/redirection/66e4fa55-fdac-4ef9-91b5-733b97d1b862",
        gatewayExpiresAt = now + 10.minutes,
    )
