package com.rusuh.app.feature.payment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaymentReturnLinkTest {
    @Test
    fun parsesAppLinkBookingId() {
        assertEquals("bk_123", parsePaymentReturnBookingId("https://app.rusuh.id/payment-result?bookingId=bk_123"))
    }

    @Test
    fun parsesCustomSchemeBookingId() {
        assertEquals("bk_123", parsePaymentReturnBookingId("rusuh://payment-result?bookingId=bk_123"))
    }

    @Test
    fun rejectsForeignMalformedAndMissingBookingLinks() {
        assertNull(parsePaymentReturnBookingId("https://evil.example/payment-result?bookingId=bk_123"))
        assertNull(parsePaymentReturnBookingId("https://app.rusuh.id/other?bookingId=bk_123"))
        assertNull(parsePaymentReturnBookingId("https://app.rusuh.id/payment-result"))
    }
}
