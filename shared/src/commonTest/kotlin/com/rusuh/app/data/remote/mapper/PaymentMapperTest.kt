package com.rusuh.app.data.remote.mapper

import com.rusuh.app.data.remote.dto.BookingDto
import com.rusuh.app.data.remote.dto.EnterPaymentDto
import com.rusuh.app.data.remote.dto.MoneyDto
import com.rusuh.app.data.remote.dto.PriceBreakdownDto
import com.rusuh.app.data.remote.dto.PriceLineDto
import com.rusuh.app.domain.model.BookingStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaymentMapperTest {
    @Test
    fun bookingMapsPaymentDetailsAndUnknownStatusWithoutFailure() {
        val booking =
            BookingDto(
                bookingId = "bk_test",
                eventId = "evt_test",
                status = "PAYMENT_PENDING",
                orderCode = "RSH-TEST",
                attendees =
                    listOf(
                        com.rusuh.app.data.remote.dto.AttendeeDto("cat", "Festival", "Rina", "3174 **** 0004"),
                    ),
                price =
                    PriceBreakdownDto(
                        lines = listOf(PriceLineDto("cat", "Festival", 1, MoneyDto(100), MoneyDto(100))),
                        ticketSubtotal = MoneyDto(100),
                        adminFee = MoneyDto(5),
                        total = MoneyDto(105),
                    ),
                expiresAt = "2026-11-14T10:10:00Z",
                createdAt = "2026-11-14T10:00:00Z",
                serverNow = "2026-11-14T10:00:00Z",
            )
        val summary = booking.toPaymentSummary("rina@example.com")

        assertEquals(BookingStatus.PAYMENT_PENDING, summary.bookingStatus)
        assertEquals("rina@example.com", summary.customerEmail)
        assertEquals(105, summary.price.total)
        assertEquals(1, summary.attendees.size)
        assertEquals("2026-11-14T10:10:00Z", summary.expiresAt.toString())
        assertEquals(
            BookingStatus.UNKNOWN,
            booking.copy(status = "NEW_BACKEND_STATE").toPaymentSummary(null).bookingStatus,
        )
    }

    @Test
    fun paymentEntryPreservesMissingRedirectUrl() {
        val snap = EnterPaymentDto("bk_test", "token", "2026-11-14T10:10:00Z", null, "2026-11-14T10:00:00Z").toDomain()

        assertEquals("token", snap.snapToken)
        assertNull(snap.redirectUrl)
    }
}
