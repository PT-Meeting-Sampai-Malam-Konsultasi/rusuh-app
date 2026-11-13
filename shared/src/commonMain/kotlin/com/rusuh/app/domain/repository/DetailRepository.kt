package com.rusuh.app.domain.repository

import com.rusuh.app.domain.model.AttendeeValidation
import com.rusuh.app.domain.model.Booking
import com.rusuh.app.domain.model.BookingRequest
import com.rusuh.app.domain.model.BookingResult
import com.rusuh.app.domain.model.DetailOffering
import com.rusuh.app.domain.model.DomainResult

/**
 * Data seam for the Detail Ticket page. Failures are thrown (non-cancellation exceptions) and
 * mapped to UI errors by the ViewModel.
 */
interface DetailRepository {
    /** Event, categories with stock, account profile and order window for [eventId]. */
    suspend fun getDetail(eventId: String): DetailOffering

    /**
     * Server-side NIK check. Validates one attendee's name and NIK for [eventId].
     * @param fieldIndex 0-based index of the ticket in the order (for rate limiting).
     * @return validation result including validation token on success.
     */
    suspend fun validateAttendee(
        eventId: String,
        fieldIndex: Int,
        fullName: String,
        nik: String,
    ): AttendeeValidation

    /**
     * Legacy isNikAccepted for stub compatibility.
     * @deprecated Use validateAttendee instead.
     */
    @Deprecated("Use validateAttendee instead", ReplaceWith("validateAttendee(...)"))
    suspend fun isNikAccepted(nik: String): Boolean = true

    /**
     * Creates the booking and holds stock.
     * @param idempotencyKey client-generated UUID, reused on retry of the same attempt.
     */
    suspend fun createBooking(
        request: BookingRequest,
        idempotencyKey: String,
    ): BookingResult

    /** Get booking by ID. */
    suspend fun getBooking(bookingId: String): DomainResult<Booking>

    /** Cancel a held booking. */
    suspend fun cancelBooking(bookingId: String): DomainResult<Booking>

    /** Get active bookings for resume flow. */
    suspend fun getActiveBookings(): DomainResult<List<Booking>>

    /** Legacy createBooking for stub compatibility. */
    @Deprecated("Use createBooking with idempotencyKey instead")
    suspend fun createBooking(request: BookingRequest): Booking
}
