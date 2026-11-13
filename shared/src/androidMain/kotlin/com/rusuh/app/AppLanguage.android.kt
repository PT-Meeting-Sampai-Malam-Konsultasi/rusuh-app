package com.rusuh.app

import android.os.LocaleList
import androidx.compose.runtime.Composable
import com.rusuh.app.data.network.AppLanguage
import java.util.Locale

@Composable
internal actual fun ProvideAppLanguageResources(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    // The platform locale is set synchronously in App.kt before composition,
    // so CMP Resources picks up the correct Locale.current. The key(language)
    // wrapper in App.kt forces full recomposition when the language changes.
    content()
}

internal actual fun applyPlatformLanguage(languageTag: String) {
    val locale = Locale.forLanguageTag(languageTag)
    // CMP Resources uses Locale.current from androidx.compose.ui.text.intl, which
    // on Android reads android.os.LocaleList.getDefault(). Setting it here ensures
    // stringResource/pluralStringResource resolve to the correct language.
    // LocaleList.setDefault also sets java.util.Locale.setDefault internally.
    LocaleList.setDefault(LocaleList(locale))
}
