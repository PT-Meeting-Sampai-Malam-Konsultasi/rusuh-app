package com.rusuh.app.data.cache

import com.rusuh.app.data.storage.AppPreferences
import com.rusuh.app.domain.repository.AccountEmailStore
import com.rusuh.app.domain.repository.DisplayNameStore

/**
 * Auth module cache (preferences excluded from Android backup, never tokens): the display name a new account typed
 * and the account email, each with the user id it belongs to.
 *
 * shortcut: the name is only on this device because the contract has no profile update; remove with the first
 * endpoint that stores it.
 */
class AuthCache(
    prefs: AppPreferences,
) : PrefsCache(prefs, PREFIX),
    DisplayNameStore,
    AccountEmailStore {
    override suspend fun get(userId: String): String? =
        if (read(KEY_USER_ID) == userId) read(KEY_NAME)?.takeIf { it.isNotBlank() } else null

    override suspend fun set(
        userId: String,
        name: String,
    ) {
        write(KEY_NAME, name)
        write(KEY_USER_ID, userId)
    }

    override suspend fun clear() {
        delete(KEY_USER_ID)
        delete(KEY_NAME)
    }

    override suspend fun email(): String? = read(KEY_EMAIL)?.takeIf { it.isNotBlank() }

    override suspend fun saveEmail(
        userId: String,
        email: String,
    ) {
        write(KEY_EMAIL, email)
        write(KEY_EMAIL_USER, userId)
    }

    override suspend fun clearEmail() {
        delete(KEY_EMAIL)
        delete(KEY_EMAIL_USER)
    }

    private companion object {
        const val PREFIX = "auth_"
        const val KEY_EMAIL = PREFIX + "account_email"
        const val KEY_EMAIL_USER = PREFIX + "account_email_user"
        const val KEY_USER_ID = PREFIX + "display_name_user"
        const val KEY_NAME = PREFIX + "display_name"
    }
}
