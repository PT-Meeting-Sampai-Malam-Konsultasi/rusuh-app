package com.rusuh.app.data.cache

import com.rusuh.app.data.storage.AppPreferences
import com.rusuh.app.domain.repository.DisplayNameStore

/**
 * Auth module cache (non-sensitive preferences, never tokens): the display name a new account typed, with the user id
 * it belongs to.
 *
 * shortcut: the name is only on this device because the contract has no profile update; remove with the first
 * endpoint that stores it.
 */
class AuthCache(
    prefs: AppPreferences,
) : PrefsCache(prefs, PREFIX),
    DisplayNameStore {
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

    private companion object {
        const val PREFIX = "auth_"
        const val KEY_USER_ID = PREFIX + "display_name_user"
        const val KEY_NAME = PREFIX + "display_name"
    }
}
