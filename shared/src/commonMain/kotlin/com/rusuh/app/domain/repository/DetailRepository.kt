package com.rusuh.app.domain.repository

import com.rusuh.app.domain.model.Booking
import com.rusuh.app.domain.model.BookingRequest
import com.rusuh.app.domain.model.DetailOffering

/**
 * Data seam for the Detail Ticket page. Failures are thrown (non-cancellation exceptions) and
 * mapped to UI errors by the ViewModel.
 */
interface DetailRepository {
    /** Event, categories with stock, account profile and order window for [eventId]. */
    suspend fun getDetail(eventId: String): DetailOffering

    /** Server-side NIK check. TODO(FR-15-M1 #80). True when the NIK is accepted. */
    suspend fun isNikAccepted(nik: String): Boolean

    /** Creates the booking and holds stock. TODO(FR-16-M1 #81). */
    suspend fun createBooking(request: BookingRequest): Booking
}
