package com.rusuh.app.ui.components

/** Default number of digits in a sign-in code (ADR-0006). */
const val OTP_CODE_LENGTH = 6

/** Visual / interaction state of an [OtpCodeInput]. */
enum class OtpCodeState {
    /** Editable; the next empty box is highlighted while focused. */
    Default,

    /** The last submitted code was rejected: danger outline and surface. */
    Error,

    /** Not editable (for example after the code expired). */
    Disabled,
}

/**
 * Keeps only ASCII digits from [raw] (so a pasted "123 456" or "12-34-56" works and
 * non-digits typed on a hardware keyboard are dropped) and caps the result at [length].
 */
fun sanitizeOtpInput(
    raw: String,
    length: Int = OTP_CODE_LENGTH,
): String = raw.filter { it in '0'..'9' }.take(length)
