package com.rusuh.app.domain.model

/** NIK format rules (FR-13): 16 ASCII digits, as printed on a KTP. */
object NikRules {
    const val LENGTH = 16

    /** True for exactly [LENGTH] ASCII digits (`Char.isDigit` would also accept other scripts). */
    fun isValid(nik: String): Boolean = nik.length == LENGTH && nik.all { it in '0'..'9' }
}

/** Outcome of validating one attendee (name + NIK) of an order. */
sealed interface AttendeeValidation {
    data object Valid : AttendeeValidation

    data object MissingName : AttendeeValidation

    data object InvalidNik : AttendeeValidation

    /** [ticketNumber] (1-based) is the EARLIER ticket of the same order already using this NIK. */
    data class DuplicateNik(val ticketNumber: Int) : AttendeeValidation

    /** The server rejected the NIK (TODO(FR-15-M1 #80)). */
    data object RejectedByServer : AttendeeValidation
}

val AttendeeValidation.isValid: Boolean get() = this == AttendeeValidation.Valid

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
        else -> AttendeeValidation.Valid
    }
