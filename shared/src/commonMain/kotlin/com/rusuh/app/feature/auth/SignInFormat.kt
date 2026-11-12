package com.rusuh.app.feature.auth

import com.rusuh.app.ui.components.OTP_CODE_LENGTH

private const val SECONDS_PER_MINUTE = 60
private const val SECOND_PAD_WIDTH = 2

/** Formats a countdown (resend cooldown, code validity) as m:ss ("1:00", "0:07"); negatives clamp to "0:00". */
fun formatResendCountdown(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val minutes = total / SECONDS_PER_MINUTE
    val rest = (total % SECONDS_PER_MINUTE).toString().padStart(SECOND_PAD_WIDTH, '0')
    return "$minutes:$rest"
}

/**
 * Light client-side check so "Kirim kode" stays disabled until the address looks like an
 * email (something@domain.tld). The server stays the authority.
 */
fun isPlausibleEmail(raw: String): Boolean {
    val email = raw.trim()
    val at = email.indexOf('@')
    if (at <= 0 || at != email.lastIndexOf('@') || email.any { it.isWhitespace() }) return false
    val domain = email.substring(at + 1)
    val dot = domain.lastIndexOf('.')
    return dot > 0 && dot < domain.length - 1 && !domain.contains("..")
}

/**
 * Whether the main button of a step can be pressed: a plausible email, a complete code, a
 * non-blank name. The expired step always allows asking for a new code. The new-account
 * checkbox is optional marketing consent, so it never blocks.
 */
fun SignInUiState.isPrimaryEnabled(): Boolean =
    when (this) {
        is SignInUiState.Email -> isPlausibleEmail(email)
        is SignInUiState.Code -> code.length == OTP_CODE_LENGTH
        is SignInUiState.WrongCode -> code.length == OTP_CODE_LENGTH
        is SignInUiState.CodeExpired -> true
        is SignInUiState.NewAccount -> fullName.isNotBlank()
    }

/**
 * Character ranges of each of [parts] inside [text] (first occurrence, parts that do not occur
 * are skipped). Lets a localized sentence with placeholders emphasise the substituted values
 * without splitting the string resource.
 */
fun findPartRanges(
    text: String,
    parts: List<String>,
): List<IntRange> =
    parts.mapNotNull { part ->
        val start = text.indexOf(part)
        if (part.isEmpty() || start < 0) null else start until start + part.length
    }
