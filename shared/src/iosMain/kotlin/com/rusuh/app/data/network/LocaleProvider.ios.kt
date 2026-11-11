package com.rusuh.app.data.network

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/** First preferred language as a BCP 47 tag (`id-ID`, `en-US`); [AppLanguage.fromCode] keeps the language part. */
internal actual fun platformLanguageCode(): String? = NSLocale.preferredLanguages.firstOrNull() as? String
