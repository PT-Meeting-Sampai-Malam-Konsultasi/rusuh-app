package com.rusuh.app.domain.usecase

import com.rusuh.app.domain.model.AttendeeValidation
import com.rusuh.app.domain.model.Booking
import com.rusuh.app.domain.model.BookingRequest
import com.rusuh.app.domain.model.BookingResult
import com.rusuh.app.domain.model.DetailOffering
import com.rusuh.app.domain.model.DomainResult
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
 * the server NIK check if local validation passes.
 */
class ValidateAttendeeUseCase(
    private val repository: DetailRepository,
) {
    suspend operator fun invoke(
        eventId: String,
        fieldIndex: Int,
        name: String,
        nik: String,
        earlierNiks: List<String>,
    ): AttendeeValidation {
        val local = validateAttendeeLocally(name, nik, earlierNiks)
        if (!local.isValid) return local
        return repository.validateAttendee(eventId, fieldIndex, name, nik)
    }
}

/**
 * Creates the booking / stock hold.
 * @param idempotencyKey client-generated UUID, reused on retry of the same attempt.
 */
class CreateBookingUseCase(
    private val repository: DetailRepository,
) {
    suspend operator fun invoke(
        request: BookingRequest,
        idempotencyKey: String,
    ): BookingResult = repository.createBooking(request, idempotencyKey)
}

/** Gets a booking by ID. */
class GetBookingUseCase(
    private val repository: DetailRepository,
) {
    suspend operator fun invoke(bookingId: String): DomainResult<Booking> = repository.getBooking(bookingId)
}

/** Cancels a held booking. */
class CancelBookingUseCase(
    private val repository: DetailRepository,
) {
    suspend operator fun invoke(bookingId: String): DomainResult<Booking> = repository.cancelBooking(bookingId)
}

/** Gets active bookings for resume flow. */
class GetActiveBookingsUseCase(
    private val repository: DetailRepository,
) {
    suspend operator fun invoke(): DomainResult<List<Booking>> = repository.getActiveBookings()
}
