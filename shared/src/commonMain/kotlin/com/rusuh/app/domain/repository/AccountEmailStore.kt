package com.rusuh.app.domain.repository

/**
 * The signed-in account's email, kept on this device so screens like Detail can show it without a network call. It
 * is saved when the sign-in code is verified and removed on sign-out; it is never sent anywhere or logged.
 */
interface AccountEmailStore {
    suspend fun email(): String?

    suspend fun saveEmail(
        userId: String,
        email: String,
    )

    suspend fun clearEmail()
}
