package com.rusuh.app

import androidx.compose.runtime.Composable
import com.rusuh.app.data.network.AppLanguage
import platform.Foundation.NSUserDefaults

@Composable
internal actual fun ProvideAppLanguageResources(
    language: AppLanguage,
    content: @Composable () -> Unit,
) = content()

internal actual fun applyPlatformLanguage(languageTag: String) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(languageTag), forKey = "AppleLanguages")
    NSUserDefaults.standardUserDefaults.synchronize()
}
