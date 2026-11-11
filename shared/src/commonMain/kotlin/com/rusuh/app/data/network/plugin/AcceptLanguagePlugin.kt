@file:Suppress("MatchingDeclarationName") // file holds the plugin value; its Config class is the only class

package com.rusuh.app.data.network.plugin

import com.rusuh.app.data.network.LocaleProvider
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders

class AcceptLanguageConfig {
    lateinit var localeProvider: LocaleProvider
}

/** Sends `Accept-Language` (`id` or `en`) on every request, unless the call already set one. */
val AcceptLanguagePlugin =
    createClientPlugin("RusuhAcceptLanguage", ::AcceptLanguageConfig) {
        val provider = pluginConfig.localeProvider
        onRequest { request, _ ->
            if (!request.headers.contains(HttpHeaders.AcceptLanguage)) {
                request.headers.append(HttpHeaders.AcceptLanguage, provider.language().tag)
            }
        }
    }
