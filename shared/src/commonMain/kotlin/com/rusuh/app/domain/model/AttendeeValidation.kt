package com.rusuh.app.domain.model

/** NIK format rules (FR-13): 16 ASCII digits, as printed on a KTP. */
object NikRules {
    const val LENGTH = 16

    /** True for exactly [LENGTH] ASCII digits (`Char.isDigit` would also accept other scripts). */
    fun isValid(nik: String): Boolean = nik.length == LENGTH && nik.all { it in '0'..'9' }
}

/** Outcome of validating one attendee (name + NIK) of an order. */
sealed interface AttendeeValidation {
    /** Server validation passed; [validationToken] is sent with the booking request. */
    data class Valid(val validationToken: String) : AttendeeValidation

    data object MissingName : AttendeeValidation

    data object InvalidNik : AttendeeValidation

    /** [ticketNumber] (1-based) is the EARLIER ticket of the same order already using this NIK. */
    data class DuplicateNik(val ticketNumber: Int) : AttendeeValidation

    /** The server rejected the NIK (format or Dukcapil lookup failed). */
    data object NikInvalid : AttendeeValidation

    /** This NIK already holds 3 seats for the event. */
    data object NikSeatTaken : AttendeeValidation

    /** This NIK is used by an earlier ticket in the same order (server-side duplicate). */
    data object ServerDuplicateNik : AttendeeValidation

    /** Dukcapil verification service is temporarily unavailable. */
    data object DukcapilUnavailable : AttendeeValidation

    /** Rate limited (429). */
    data class RateLimited(val retryAfterSeconds: Int?) : AttendeeValidation

    /** Admission token expired or invalid (back to waiting room). */
    data object AdmissionInvalid : AttendeeValidation

    /** Network error during validation. */
    data object NetworkError : AttendeeValidation

    /** Any other server error. */
    data class ServerError(val code: Int, val errorCode: ErrorCode) : AttendeeValidation

    /** Legacy: kept for stub compatibility. */
    data object RejectedByServer : AttendeeValidation
}

val AttendeeValidation.isValid: Boolean get() = this is AttendeeValidation.Valid

/**
 * Local (no I/O) attendee rules: name present, NIK is 16 digits, and the NIK is not used by an
 * earlier ticket in [earlierNiks] (one NIK = one ticket per order).
 */
fun validateAttendeeLocally(
    name: String,
    nik: String,
    earlierNiks: List<String>,
): AttendeeValidation =
    when {
        name.isBlank() -> AttendeeValidation.MissingName
        !NikRules.isValid(nik) -> AttendeeValidation.InvalidNik
        earlierNiks.contains(nik) -> AttendeeValidation.DuplicateNik(earlierNiks.indexOf(nik) + 1)
        // Local pass: return a placeholder token; server validation will replace it.
        else -> AttendeeValidation.Valid("")
    }
