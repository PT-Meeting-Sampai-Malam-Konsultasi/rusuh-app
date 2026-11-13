package com.rusuh.app.data.repository

import com.rusuh.app.data.clock.MonotonicServerClock
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.isNetworkFailure
import com.rusuh.app.data.remote.BookingApiService
import com.rusuh.app.data.remote.dto.BookingTicketDto
import com.rusuh.app.data.remote.dto.CreateBookingRequestDto
import com.rusuh.app.data.remote.dto.ValidateAttendeeRequestDto
import com.rusuh.app.data.remote.mapper.expiresAtMillis
import com.rusuh.app.data.remote.mapper.serverNowMillis
import com.rusuh.app.data.remote.mapper.toDomain
import com.rusuh.app.data.remote.mapper.toDomainCategories
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.AccountProfile
import com.rusuh.app.domain.model.AttendeeValidation
import com.rusuh.app.domain.model.Booking
import com.rusuh.app.domain.model.BookingRequest
import com.rusuh.app.domain.model.BookingResult
import com.rusuh.app.domain.model.DetailOffering
import com.rusuh.app.domain.model.DomainException
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import com.rusuh.app.domain.model.EventDetail
import com.rusuh.app.domain.repository.DetailRepository

/**
 * Default retry-after seconds when the server returns 429 without a Retry-After header.
 * The UI must never display "null"; this is a reasonable minimum backoff.
 */
private const val DEFAULT_RETRY_AFTER_SECONDS = 30

/**
 * Real [DetailRepository] backed by the booking API.
 *
 * - Categories: GET /core/events/{eventId}/categories (requires Bearer + X-Admission-Token).
 * - Attendee validation: POST /core/events/{eventId}/attendees/validate.
 * - Create booking: POST /core/bookings with Idempotency-Key header.
 * - Get/cancel booking: GET/POST /core/bookings/{bookingId}/...
 *
 * The event detail (title, subtitle, meta) comes from the Home cache or is fetched separately;
 * the categories endpoint only provides category data. For now, we use placeholder event data
 * until the event detail API lands.
 *
 * The categories response also carries `serverNow`; this is fed to the [ServerClock] (when it
 * supports sync) so Detail's countdown is server-aligned.
 */
@Suppress("TooManyFunctions")
class RealDetailRepository(
    private val api: BookingApiService,
    private val serverClock: ServerClock,
) : DetailRepository {
    override suspend fun getDetail(eventId: String): DetailOffering {
        val result = api.getCategories(eventId)
        when (result) {
            is ApiResult.Success -> {
                val dto = result.data
                // Sync the server clock from the categories response.
                syncClock(dto.serverNowMillis())
                return DetailOffering(
                    // TODO(FR-05-M4 #38): fetch event detail from cache or dedicated endpoint.
                    event =
                        EventDetail(
                            id = dto.eventId,
                            title = "Event",
                            subtitle = null,
                            meta = "",
                        ),
                    categories = dto.toDomainCategories(),
                    // TODO(FR-05-M4 #38): fetch profile from auth/session.
                    profile = AccountProfile(name = "", email = ""),
                    adminFeeRupiah = dto.adminFee.amount,
                    reservedUnits = dto.maxTickets,
                    expiresAtMillis = dto.expiresAtMillis(),
                )
            }
            is ApiResult.Error -> throw result.toException()
            is ApiResult.Failure -> throw result.toException()
        }
    }

    override suspend fun validateAttendee(
        eventId: String,
        fieldIndex: Int,
        fullName: String,
        nik: String,
    ): AttendeeValidation {
        val request =
            ValidateAttendeeRequestDto(
                fieldIndex = fieldIndex,
                fullName = fullName,
                nik = nik,
            )
        return when (val result = api.validateAttendee(eventId, request)) {
            is ApiResult.Success -> AttendeeValidation.Valid(result.data.validationToken)
            is ApiResult.Error -> mapValidationError(result)
            is ApiResult.Failure ->
                if (isNetworkFailure(result.throwable)) {
                    AttendeeValidation.NetworkError
                } else {
                    AttendeeValidation.ServerError(0, ErrorCode.UNKNOWN)
                }
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use validateAttendee instead", ReplaceWith("validateAttendee(...)"))
    override suspend fun isNikAccepted(nik: String): Boolean = true

    override suspend fun createBooking(
        request: BookingRequest,
        idempotencyKey: String,
    ): BookingResult {
        val dto =
            CreateBookingRequestDto(
                eventId = request.eventId,
                tickets =
                    request.tickets.map { ticket ->
                        BookingTicketDto(
                            categoryId = ticket.categoryId,
                            validationToken = ticket.validationToken,
                        )
                    },
            )
        return when (val result = api.createBooking(dto, idempotencyKey)) {
            is ApiResult.Success -> BookingResult.Success(result.data.toDomain())
            is ApiResult.Error -> mapBookingError(result)
            is ApiResult.Failure ->
                if (isNetworkFailure(result.throwable)) {
                    BookingResult.NetworkError
                } else {
                    BookingResult.ServerError(0, ErrorCode.UNKNOWN)
                }
        }
    }

    override suspend fun getBooking(bookingId: String): DomainResult<Booking> =
        api.getBooking(bookingId).toDomainResult { it.toDomain() }

    override suspend fun cancelBooking(bookingId: String): DomainResult<Booking> =
        api.cancelBooking(bookingId).toDomainResult { it.toDomain() }

    override suspend fun getActiveBookings(): DomainResult<List<Booking>> =
        api.getActiveBookings().toDomainResult { it.items.map { dto -> dto.toDomain() } }

    @Deprecated("Use createBooking with idempotencyKey instead")
    override suspend fun createBooking(request: BookingRequest): Booking {
        val result = createBooking(request, newIdempotencyKey())
        return when (result) {
            is BookingResult.Success -> result.booking
            else -> throw DomainException(DomainResult.Error(code = 0, errorCode = ErrorCode.UNKNOWN))
        }
    }

    private fun mapValidationError(error: ApiResult.Error): AttendeeValidation {
        val errorCode = error.problem?.code ?: ErrorCode.UNKNOWN
        return mapValidationByStatusCode(error, errorCode)
            ?: mapValidationByErrorCode(error.code, errorCode)
            ?: AttendeeValidation.ServerError(error.code, errorCode)
    }

    /** Maps validation errors by HTTP status code (410, 429). Returns null if not matched. */
    private fun mapValidationByStatusCode(
        error: ApiResult.Error,
        errorCode: ErrorCode,
    ): AttendeeValidation? =
        when (error.code) {
            HTTP_FORBIDDEN ->
                if (errorCode == ErrorCode.ADMISSION_INVALID) {
                    AttendeeValidation.AdmissionInvalid
                } else {
                    null
                }
            HTTP_GONE -> AttendeeValidation.AdmissionInvalid
            HTTP_TOO_MANY_REQUESTS ->
                AttendeeValidation.RateLimited(error.retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS)
            else -> null
        }

    /** Maps validation errors by error code (NIK_INVALID, etc). Returns null if not matched. */
    private fun mapValidationByErrorCode(
        statusCode: Int,
        errorCode: ErrorCode,
    ): AttendeeValidation? =
        when {
            statusCode == 422 && errorCode == ErrorCode.NIK_INVALID -> AttendeeValidation.NikInvalid
            statusCode == 409 && errorCode == ErrorCode.NIK_SEAT_TAKEN -> AttendeeValidation.NikSeatTaken
            statusCode == 409 && errorCode == ErrorCode.DUPLICATE_NIK -> AttendeeValidation.ServerDuplicateNik
            statusCode == HTTP_SERVICE_UNAVAILABLE && errorCode == ErrorCode.DUKCAPIL_UNAVAILABLE ->
                AttendeeValidation.DukcapilUnavailable
            else -> null
        }

    private fun mapBookingError(error: ApiResult.Error): BookingResult {
        val errorCode = error.problem?.code ?: ErrorCode.UNKNOWN
        return mapByStatusCode(error.code, errorCode, error)
            ?: mapConflictError(error, errorCode)
            ?: BookingResult.ServerError(error.code, errorCode)
    }

    /**
     * Maps errors by HTTP status code (401, 403, 410, 429, 5xx). Returns null if not matched.
     */
    private fun mapByStatusCode(
        statusCode: Int,
        errorCode: ErrorCode,
        error: ApiResult.Error,
    ): BookingResult? =
        when (statusCode) {
            HTTP_UNAUTHORIZED -> BookingResult.SessionExpired
            HTTP_FORBIDDEN ->
                if (errorCode == ErrorCode.ADMISSION_INVALID) {
                    BookingResult.AdmissionInvalid
                } else {
                    null
                }
            // 410: admission expired mid-booking
            HTTP_GONE -> BookingResult.AdmissionInvalid
            // 429: rate limited, default to sane constant when Retry-After is missing
            HTTP_TOO_MANY_REQUESTS ->
                BookingResult.RateLimited(error.retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS)
            // 5xx server errors: map to a retryable error with the server's retry hint
            HTTP_SERVICE_UNAVAILABLE ->
                BookingResult.ServiceUnavailable(error.retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS)
            in HTTP_SERVER_ERROR_RANGE -> BookingResult.ServiceUnavailable(DEFAULT_RETRY_AFTER_SECONDS)
            else -> null
        }

    /** Maps 409 Conflict errors by their error code. Returns null if not a 409 or unknown code. */
    private fun mapConflictError(
        error: ApiResult.Error,
        errorCode: ErrorCode,
    ): BookingResult? {
        if (error.code != 409) return null
        return when (errorCode) {
            ErrorCode.QUANTITY_OVER_RESERVATION -> BookingResult.QuantityOverReservation
            ErrorCode.CATEGORY_SOLD_OUT -> {
                val categoryId = error.problem?.extras?.get("categoryId")?.toString()?.removeSurrounding("\"")
                BookingResult.CategorySoldOut(categoryId)
            }
            ErrorCode.ACCOUNT_HAS_ACTIVE_BOOKING -> BookingResult.AccountHasActiveBooking
            ErrorCode.ATTENDEE_NOT_VALIDATED -> BookingResult.AttendeeNotValidated
            ErrorCode.NIK_SEAT_TAKEN -> BookingResult.NikSeatTaken
            ErrorCode.DUPLICATE_NIK -> BookingResult.DuplicateNik
            ErrorCode.BOOKING_NOT_HELD -> BookingResult.BookingNotHeld
            else -> null
        }
    }

    /**
     * Syncs the server clock from the categories response if the clock supports it.
     * The [MonotonicServerClock] is the only implementation that supports sync; others (stub/test) ignore this.
     */
    private fun syncClock(serverNowMillis: Long) {
        if (serverNowMillis > 0 && serverClock is MonotonicServerClock) {
            serverClock.recordServerNow(serverNowMillis)
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_GONE = 410
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVICE_UNAVAILABLE = 503
        val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}

/** Generates a new idempotency key (UUID v4). */
internal expect fun newIdempotencyKey(): String
