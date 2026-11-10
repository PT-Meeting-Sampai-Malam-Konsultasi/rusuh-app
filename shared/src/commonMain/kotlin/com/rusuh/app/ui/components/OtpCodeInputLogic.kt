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
 * Decides when [OtpCodeInput]'s `onComplete` fires: once per completed value. It fires again
 * only after the value has changed away from complete (fewer digits) and back.
 */
class OtpCompletionTracker(
    private val length: Int = OTP_CODE_LENGTH,
) {
    private var last: String? = null

    /** Reports the value currently shown; any incomplete value re-arms the tracker. */
    fun onCurrentValue(current: String) {
        if (current.length < length) last = null
    }

    /** True when [next] is a newly completed value that has not been reported yet. */
    fun shouldComplete(next: String): Boolean {
        if (next.length != length || next == last) return false
        last = next
        return true
    }
}

/**
 * Keeps only ASCII digits from [raw] (so a pasted "123 456" or "12-34-56" works and
 * non-digits typed on a hardware keyboard are dropped) and caps the result at [length].
 */
fun sanitizeOtpInput(
    raw: String,
    length: Int = OTP_CODE_LENGTH,
): String = raw.filter { it in '0'..'9' }.take(length)
