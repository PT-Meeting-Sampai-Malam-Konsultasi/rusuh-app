package com.rusuh.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.rusuh.app.data.network.AppLanguage
import com.rusuh.app.data.network.LocaleProvider
import com.rusuh.app.data.network.platformLanguageCode
import com.rusuh.app.data.storage.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.Indonesian }

internal expect fun applyPlatformLanguage(languageTag: String)

@Composable
internal expect fun ProvideAppLanguageResources(
    language: AppLanguage,
    content: @Composable () -> Unit,
)

class AppLanguageState(private val preferences: () -> AppPreferences) : LocaleProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableLanguage = MutableStateFlow(AppLanguage.fromCode(platformLanguageCode()))
    val language: StateFlow<AppLanguage> = mutableLanguage.asStateFlow()

    init {
        scope.launch {
            runCatching { preferences().get(KEY) }.getOrNull()?.let { stored ->
                mutableLanguage.value = AppLanguage.entries.firstOrNull { it.tag == stored } ?: mutableLanguage.value
            }
        }
    }

    override fun language(): AppLanguage = mutableLanguage.value

    fun select(language: AppLanguage) {
        mutableLanguage.value = language
        scope.launch { runCatching { preferences().set(KEY, language.tag) } }
    }

    private companion object {
        const val KEY = "app_language"
    }
}
