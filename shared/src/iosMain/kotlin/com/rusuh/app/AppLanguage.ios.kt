package com.rusuh.app

import platform.Foundation.NSUserDefaults

internal actual fun applyPlatformLanguage(languageTag: String) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(languageTag), forKey = "AppleLanguages")
    NSUserDefaults.standardUserDefaults.synchronize()
}
