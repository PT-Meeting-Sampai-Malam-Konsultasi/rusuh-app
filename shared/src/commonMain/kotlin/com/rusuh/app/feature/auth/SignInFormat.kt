package com.rusuh.app.feature.auth

private const val SECONDS_PER_MINUTE = 60
private const val SECOND_PAD_WIDTH = 2

/** Formats a resend cooldown as m:ss ("1:00", "0:07"); negative values clamp to "0:00". */
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
