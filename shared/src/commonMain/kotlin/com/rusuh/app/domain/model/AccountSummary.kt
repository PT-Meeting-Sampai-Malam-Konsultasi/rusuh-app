package com.rusuh.app.domain.model

/**
 * What the Account page shows. [name] is the server name, else the one typed at sign-up on this device, else null
 * (the UI then shows the part of the email before the "@").
 */
data class AccountSummary(
    val userId: String,
    val email: String,
    val name: String?,
) {
    override fun toString(): String = "AccountSummary(userId=$userId, <redacted>)"
}
