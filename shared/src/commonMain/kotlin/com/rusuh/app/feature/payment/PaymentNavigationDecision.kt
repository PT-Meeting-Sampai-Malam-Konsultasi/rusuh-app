package com.rusuh.app.feature.payment

import io.ktor.http.Url

sealed interface PaymentNavigationDecision {
    data object Stay : PaymentNavigationDecision

    data object Close : PaymentNavigationDecision

    data class OpenExternal(val url: String) : PaymentNavigationDecision

    data object Block : PaymentNavigationDecision
}

@Suppress("ReturnCount")
fun decidePaymentNavigation(
    value: String,
    bookingId: String,
): PaymentNavigationDecision {
    val url = runCatching { Url(value) }.getOrNull() ?: return PaymentNavigationDecision.Block
    val scheme = url.protocol.name.lowercase()
    if (scheme == "https" || scheme == "http") {
        if (scheme != "https") return PaymentNavigationDecision.Block
        val host = url.host.lowercase()
        if (host == "gojek.link" || host == "gopay.co.id" || host.endsWith(".gopay.co.id")) {
            return PaymentNavigationDecision.OpenExternal(value)
        }
        val resultUrl =
            url.host == "app.rusuh.id" && url.encodedPath == "/payment-result" &&
                url.parameters["bookingId"] == bookingId
        val snapResult =
            listOf("transaction_status", "status_code", "order_id").any { key ->
                url.parameters[key] != null
            }
        return if (resultUrl || snapResult) PaymentNavigationDecision.Close else PaymentNavigationDecision.Stay
    }
    if (scheme == "tel" || scheme == "mailto") return PaymentNavigationDecision.Block
    return PaymentNavigationDecision.OpenExternal(value)
}
