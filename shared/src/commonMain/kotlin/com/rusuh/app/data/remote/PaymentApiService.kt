package com.rusuh.app.data.remote

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.plugin.admissionToken
import com.rusuh.app.data.remote.dto.BookingDto
import com.rusuh.app.data.remote.dto.EnterPaymentDto
import com.rusuh.app.data.remote.dto.ProblemDetails
import com.rusuh.app.data.storage.TokenStore
import com.rusuh.app.domain.model.ErrorCode

/**
 * Payment transitions on the core booking API.
 */
class PaymentApiService(
    private val api: ApiClient,
    private val tokenStore: TokenStore,
) {
    suspend fun getBooking(bookingId: String): ApiResult<BookingDto> = api.get("$EP_BOOKINGS/${pathSegment(bookingId)}")

    /**
     * `POST /core/bookings/{bookingId}/payment` performs the atomic hold and creates the first Snap attempt.
     */
    suspend fun startSnap(bookingId: String): ApiResult<EnterPaymentDto> {
        val token = tokenStore.getAdmissionToken() ?: return admissionMissingError()
        return api.post("$EP_BOOKINGS/${pathSegment(bookingId)}/payment") { admissionToken(token) }
    }

    /**
     * `POST /core/bookings/{bookingId}/check-status` rechecks Midtrans and returns the authoritative booking.
     */
    suspend fun checkStatus(bookingId: String): ApiResult<BookingDto> {
        val token = tokenStore.getAdmissionToken() ?: return admissionMissingError()
        return api.post("$EP_BOOKINGS/${pathSegment(bookingId)}/check-status") { admissionToken(token) }
    }

    private fun <T> admissionMissingError(): ApiResult<T> =
        ApiResult.Error(
            403,
            ProblemDetails(
                type = "about:blank",
                title = "Admission token missing",
                status = 403,
                code = ErrorCode.ADMISSION_INVALID,
                detail = "Admission token missing",
                traceId = null,
            ),
        )

    private companion object {
        const val EP_BOOKINGS = "/core/bookings"
    }
}
