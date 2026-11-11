package com.rusuh.app.data.network

import java.util.Locale

/** Android reports Indonesian as the legacy `in`; [AppLanguage.fromCode] treats non-English as Indonesian. */
internal actual fun platformLanguageCode(): String? = Locale.getDefault().language
