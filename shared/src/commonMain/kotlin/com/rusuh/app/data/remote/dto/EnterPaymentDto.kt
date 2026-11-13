package com.rusuh.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EnterPaymentDto(
    @SerialName("bookingId") val bookingId: String = "",
    @SerialName("snapToken") val snapToken: String = "",
    @SerialName("paymentExpiresAt") val paymentExpiresAt: String = "",
    @SerialName("redirectUrl") val redirectUrl: String? = null,
    @SerialName("serverNow") val serverNow: String = "",
)
