package com.rusuh.app.feature.payment

import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentNavigationDecisionTest {
    @Test
    fun keepsHttpsBankPageInsideWebView() {
        assertEquals(PaymentNavigationDecision.Stay, decidePaymentNavigation("https://bank.example/3ds", "bk_1"))
    }

    @Test
    fun closesForMatchingReturnUrl() {
        assertEquals(
            PaymentNavigationDecision.Close,
            decidePaymentNavigation("https://app.rusuh.id/payment-result?bookingId=bk_1", "bk_1"),
        )
    }

    @Test
    fun ignoresReturnUrlForDifferentBooking() {
        assertEquals(
            PaymentNavigationDecision.Stay,
            decidePaymentNavigation("https://app.rusuh.id/payment-result?bookingId=bk_2", "bk_1"),
        )
    }

    @Test
    fun closesForMidtransTransactionResult() {
        assertEquals(
            PaymentNavigationDecision.Close,
            decidePaymentNavigation("https://pay.example/done?transaction_status=pending", "bk_1"),
        )
    }

    @Test
    fun opensWalletSchemeExternally() {
        assertEquals(
            PaymentNavigationDecision.OpenExternal("gojek://pay/123"),
            decidePaymentNavigation("gojek://pay/123", "bk_1"),
        )
    }

    @Test
    fun opensGojekAndGopayUniversalLinksExternally() {
        listOf(
            "https://gojek.link/pay/123",
            "https://gopay.co.id/pay/123",
            "https://checkout.gopay.co.id/pay/123",
            "https://CHECKOUT.GOPAY.CO.ID/pay/123",
        ).forEach { url ->
            assertEquals(PaymentNavigationDecision.OpenExternal(url), decidePaymentNavigation(url, "bk_1"))
        }
    }

    @Test
    fun doesNotOpenLookalikeGopayHostsExternally() {
        assertEquals(
            PaymentNavigationDecision.Stay,
            decidePaymentNavigation("https://gopay.co.id.example.com/pay/123", "bk_1"),
        )
    }

    @Test
    fun blocksCleartextGopayLinks() {
        assertEquals(
            PaymentNavigationDecision.Block,
            decidePaymentNavigation("http://gopay.co.id/pay/123", "bk_1"),
        )
    }

    @Test
    fun blocksCleartextAndContactLinks() {
        assertEquals(PaymentNavigationDecision.Block, decidePaymentNavigation("http://bank.example", "bk_1"))
        assertEquals(PaymentNavigationDecision.Block, decidePaymentNavigation("mailto:help@example.com", "bk_1"))
    }
}
