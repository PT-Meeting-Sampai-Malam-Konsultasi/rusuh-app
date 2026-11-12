package com.rusuh.app.domain.repository

/**
 * The name a new account typed during sign-up, kept on this device only (the API has no profile update). It is
 * the display name when `GET /me` returns none. One record: the name belongs to the user id it was stored for, and
 * [clear] removes it (sign-out).
 */
interface DisplayNameStore {
    suspend fun get(userId: String): String?

    suspend fun set(
        userId: String,
        name: String,
    )

    suspend fun clear()
}
