package com.rusuh.app.data.remote

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.idempotencyKey
import com.rusuh.app.data.network.jsonBody
import com.rusuh.app.data.remote.dto.ActiveBookingsResponseDto
import com.rusuh.app.data.remote.dto.BookingDto
import com.rusuh.app.data.remote.dto.CategoriesResponseDto
import com.rusuh.app.data.remote.dto.CreateBookingRequestDto
import com.rusuh.app.data.remote.dto.ValidateAttendeeRequestDto
import com.rusuh.app.data.remote.dto.ValidateAttendeeResponseDto

/**
 * Booking API (core service): categories, attendee validation, booking create/get/cancel.
 * All endpoints except `/events` require a Bearer token; attendee validation and booking create also require
 * the `X-Admission-Token` header (from the queue claim, see `TokenStore`).
 */
class BookingApiService(
    private val api: ApiClient,
) {
    /** `GET /core/events/{eventId}/categories`: categories, prices, stock, order countdown. */
    suspend fun getCategories(eventId: String): ApiResult<CategoriesResponseDto> =
        api.get("$EP_EVENTS/$eventId/categories")

    /**
     * `POST /core/events/{eventId}/attendees/validate`: validate one attendee's name + NIK.
     * Requires Bearer + X-Admission-Token.
     */
    suspend fun validateAttendee(
        eventId: String,
        request: ValidateAttendeeRequestDto,
    ): ApiResult<ValidateAttendeeResponseDto> =
        api.post("$EP_EVENTS/$eventId/attendees/validate") {
            jsonBody(request)
        }

    /**
     * `POST /core/bookings`: create a booking (SELECTION status, 5-minute window).
     * Requires Bearer + X-Admission-Token + Idempotency-Key.
     * @param idempotencyKey client-generated UUID, reused on retry.
     */
    suspend fun createBooking(
        request: CreateBookingRequestDto,
        idempotencyKey: String,
    ): ApiResult<BookingDto> =
        api.post(EP_BOOKINGS) {
            jsonBody(request)
            idempotencyKey(idempotencyKey)
        }

    /** `GET /core/bookings/{bookingId}`: booking status (source of truth for result screens). */
    suspend fun getBooking(bookingId: String): ApiResult<BookingDto> = api.get("$EP_BOOKINGS/$bookingId")

    /** `POST /core/bookings/{bookingId}/cancel`: cancel a held booking and release the tickets. */
    suspend fun cancelBooking(bookingId: String): ApiResult<BookingDto> = api.post("$EP_BOOKINGS/$bookingId/cancel")

    /** `GET /core/bookings/active`: my active bookings (resume after app was closed). */
    suspend fun getActiveBookings(): ApiResult<ActiveBookingsResponseDto> = api.get("$EP_BOOKINGS/active")

    private companion object {
        const val EP_EVENTS = "/core/events"
        const val EP_BOOKINGS = "/core/bookings"
    }
}
