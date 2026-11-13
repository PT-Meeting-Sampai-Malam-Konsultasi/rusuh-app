package com.rusuh.app.feature.payment

import io.ktor.http.Url
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

fun parsePaymentReturnBookingId(value: String): String? =
    runCatching {
        val url = Url(value)
        val validRoute =
            (url.protocol.name == "https" && url.host == "app.rusuh.id" && url.encodedPath == "/payment-result") ||
                (url.protocol.name == "rusuh" && url.host == "payment-result" && url.encodedPath.isEmpty())
        if (!validRoute) return null
        url.parameters["bookingId"]?.takeIf { it.isNotBlank() && it.length <= 128 }
    }.getOrNull()

object PaymentReturnLinks {
    private val channel = Channel<String>(Channel.BUFFERED)
    val incoming = channel.receiveAsFlow()

    fun receive(url: String) {
        channel.trySend(url)
    }
}

fun handlePaymentReturnUrl(url: String) {
    PaymentReturnLinks.receive(url)
}
