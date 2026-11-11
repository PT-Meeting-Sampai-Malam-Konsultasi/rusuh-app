package com.rusuh.app.data.network

/** Languages the backend and the app strings support. Indonesian is the default (EPIC-00-M10). */
enum class AppLanguage(
    val tag: String,
) {
    Indonesian("id"),
    English("en"),
    ;

    companion object {
        /** Maps a platform language code (`id`, `in`, `en-US`, ...) to a supported one; others give [Indonesian]. */
        fun fromCode(code: String?): AppLanguage {
            val language = code?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')
            return if (language == English.tag) English else Indonesian
        }
    }
}

/** Source of the active UI language, sent as `Accept-Language` on every request (read per request). */
fun interface LocaleProvider {
    fun language(): AppLanguage
}

/** The device language code (`id`, `en`, ...) as the platform reports it, or null when unknown. */
internal expect fun platformLanguageCode(): String?

/** [LocaleProvider] following the device language; anything but English resolves to Indonesian. */
class SystemLocaleProvider : LocaleProvider {
    override fun language(): AppLanguage = AppLanguage.fromCode(platformLanguageCode())
}
