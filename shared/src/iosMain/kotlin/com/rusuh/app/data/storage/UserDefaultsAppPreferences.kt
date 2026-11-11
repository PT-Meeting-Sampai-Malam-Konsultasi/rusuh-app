package com.rusuh.app.data.storage

import platform.Foundation.NSUserDefaults

/** [AppPreferences] on NSUserDefaults (standard suite). */
class UserDefaultsAppPreferences(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AppPreferences {
    override suspend fun get(key: String): String? = defaults.stringForKey(key)

    override suspend fun set(
        key: String,
        value: String,
    ) = defaults.setObject(value, forKey = key)

    override suspend fun remove(key: String) = defaults.removeObjectForKey(key)
}
