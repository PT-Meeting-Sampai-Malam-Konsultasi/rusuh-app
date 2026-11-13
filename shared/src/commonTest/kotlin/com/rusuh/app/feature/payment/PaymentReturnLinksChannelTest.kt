package com.rusuh.app.feature.payment

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentReturnLinksChannelTest {
    @Test
    fun handledUrlIsDeliveredToTheIncomingFlow() =
        runTest {
            handlePaymentReturnUrl("rusuh://payment-result?bookingId=bk_channel_1")
            assertEquals("rusuh://payment-result?bookingId=bk_channel_1", PaymentReturnLinks.incoming.first())
        }

    @Test
    fun receivedUrlsArriveInOrder() =
        runTest {
            PaymentReturnLinks.receive("https://app.rusuh.id/payment-result?bookingId=bk_a")
            PaymentReturnLinks.receive("https://app.rusuh.id/payment-result?bookingId=bk_b")
            assertEquals("https://app.rusuh.id/payment-result?bookingId=bk_a", PaymentReturnLinks.incoming.first())
            assertEquals("https://app.rusuh.id/payment-result?bookingId=bk_b", PaymentReturnLinks.incoming.first())
        }
}
