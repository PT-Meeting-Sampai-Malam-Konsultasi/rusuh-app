package com.rusuh.app

import android.content.Context
import android.content.res.Configuration
import org.koin.core.context.GlobalContext
import java.util.Locale

internal actual fun applyPlatformLanguage(languageTag: String) {
    val locale = Locale.forLanguageTag(languageTag)
    Locale.setDefault(locale)
    val context = GlobalContext.get().get<Context>()
    val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
    @Suppress("DEPRECATION")
    context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
}
