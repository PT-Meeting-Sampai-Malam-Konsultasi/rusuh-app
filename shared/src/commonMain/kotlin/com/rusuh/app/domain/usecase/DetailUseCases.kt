package com.rusuh.app.domain.usecase

import com.rusuh.app.domain.model.AttendeeValidation
import com.rusuh.app.domain.model.Booking
import com.rusuh.app.domain.model.BookingRequest
import com.rusuh.app.domain.model.DetailOffering
import com.rusuh.app.domain.model.isValid
import com.rusuh.app.domain.model.validateAttendeeLocally
import com.rusuh.app.domain.repository.DetailRepository

/** Loads the Detail page content for an event. */
class GetDetailUseCase(
    private val repository: DetailRepository,
) {
    suspend operator fun invoke(eventId: String): DetailOffering = repository.getDetail(eventId)
}

/**
 * Validates one attendee: local rules first (name, 16 digits, no duplicate in the order), then
 * the server NIK check (TODO(FR-15-M1 #80), stubbed to accept).
 */
class ValidateAttendeeUseCase(
    private val repository: DetailRepository,
) {
    suspend operator fun invoke(
        name: String,
        nik: String,
        earlierNiks: List<String>,
    ): AttendeeValidation {
        val local = validateAttendeeLocally(name, nik, earlierNiks)
        return when {
            !local.isValid -> local
            repository.isNikAccepted(nik) -> AttendeeValidation.Valid
            else -> AttendeeValidation.RejectedByServer
        }
    }
}

/** Creates the booking / stock hold. TODO(FR-16-M1 #81): stubbed until the booking API exists. */
class CreateBookingUseCase(
    private val repository: DetailRepository,
) {
    suspend operator fun invoke(request: BookingRequest): Booking = repository.createBooking(request)
}
