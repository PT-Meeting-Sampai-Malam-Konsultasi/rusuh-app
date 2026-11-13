package com.rusuh.app.data.remote

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.idempotencyKey
import com.rusuh.app.data.network.jsonBody
import com.rusuh.app.data.network.plugin.admissionToken
import com.rusuh.app.data.remote.dto.ActiveBookingsResponseDto
import com.rusuh.app.data.remote.dto.BookingDto
import com.rusuh.app.data.remote.dto.CategoriesResponseDto
import com.rusuh.app.data.remote.dto.CreateBookingRequestDto
import com.rusuh.app.data.remote.dto.ProblemDetails
import com.rusuh.app.data.remote.dto.ValidateAttendeeRequestDto
import com.rusuh.app.data.remote.dto.ValidateAttendeeResponseDto
import com.rusuh.app.data.storage.TokenStore
import com.rusuh.app.domain.model.ErrorCode

/**
 * Booking API (core service): categories, attendee validation, booking create/get/cancel.
 * All endpoints except `/events` require a Bearer token; attendee validation and booking create also require
 * the `X-Admission-Token` header (from the queue claim, see [TokenStore]).
 *
 * The admission token is read from [tokenStore] at call time. Calls that require the token check for its
 * presence before sending the request; a missing token returns a 403-style error result without network I/O
 * so the UI can navigate back to the waiting room cleanly.
 */
class BookingApiService(
    private val api: ApiClient,
    private val tokenStore: TokenStore,
) {
    /**
     * `GET /core/events/{eventId}/categories`: categories, prices, stock, order countdown.
     * Requires Bearer + X-Admission-Token per the OpenAPI contract.
     */
    suspend fun getCategories(eventId: String): ApiResult<CategoriesResponseDto> {
        val token =
            tokenStore.getAdmissionToken()
                ?: return admissionMissingError()
        return api.get("$EP_EVENTS/$eventId/categories") {
            admissionToken(token)
        }
    }

    /**
     * `POST /core/events/{eventId}/attendees/validate`: validate one attendee's name + NIK.
     * Requires Bearer + X-Admission-Token.
     */
    suspend fun validateAttendee(
        eventId: String,
        request: ValidateAttendeeRequestDto,
    ): ApiResult<ValidateAttendeeResponseDto> {
        val token =
            tokenStore.getAdmissionToken()
                ?: return admissionMissingError()
        return api.post("$EP_EVENTS/$eventId/attendees/validate") {
            admissionToken(token)
            jsonBody(request)
        }
    }

    /**
     * `POST /core/bookings`: create a booking (SELECTION status, 5-minute window).
     * Requires Bearer + X-Admission-Token + Idempotency-Key.
     * @param idempotencyKey client-generated UUID, reused on retry.
     */
    suspend fun createBooking(
        request: CreateBookingRequestDto,
        idempotencyKey: String,
    ): ApiResult<BookingDto> {
        val token =
            tokenStore.getAdmissionToken()
                ?: return admissionMissingError()
        return api.post(EP_BOOKINGS) {
            admissionToken(token)
            jsonBody(request)
            idempotencyKey(idempotencyKey)
        }
    }

    /** `GET /core/bookings/{bookingId}`: booking status (source of truth for result screens). */
    suspend fun getBooking(bookingId: String): ApiResult<BookingDto> = api.get("$EP_BOOKINGS/$bookingId")

    /** `POST /core/bookings/{bookingId}/cancel`: cancel a held booking and release the tickets. */
    suspend fun cancelBooking(bookingId: String): ApiResult<BookingDto> = api.post("$EP_BOOKINGS/$bookingId/cancel")

    /** `GET /core/bookings/active`: my active bookings (resume after app was closed). */
    suspend fun getActiveBookings(): ApiResult<ActiveBookingsResponseDto> = api.get("$EP_BOOKINGS/active")

    /**
     * Returns a synthetic 403 error with ADMISSION_INVALID code when the admission token is missing.
     * This avoids a network round trip and lets the caller navigate back to the waiting room.
     */
    private fun <T> admissionMissingError(): ApiResult<T> =
        ApiResult.Error(
            code = HTTP_FORBIDDEN,
            problem =
                ProblemDetails(
                    type = "https://api.rusuh.id/problems/admission-invalid",
                    title = "Admission token missing",
                    status = HTTP_FORBIDDEN,
                    code = ErrorCode.ADMISSION_INVALID,
                    detail = "No admission token available. Re-join the queue.",
                    traceId = null,
                ),
        )

    private companion object {
        const val EP_EVENTS = "/core/events"
        const val EP_BOOKINGS = "/core/bookings"
        const val HTTP_FORBIDDEN = 403
    }
}
