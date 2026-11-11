package com.rusuh.app.data.network.plugin

import com.rusuh.app.data.clock.ServerTimeSink
import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiClient.Companion.configurePlugins
import com.rusuh.app.data.network.ApiClient.Companion.installHostAuth
import com.rusuh.app.data.network.ApiClient.Companion.useBaseUrl
import com.rusuh.app.data.network.HostAuth
import com.rusuh.app.data.network.LocaleProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData

internal const val TEST_BASE_URL = "https://api.test"

internal class TestHttp(
    val engine: MockEngine,
    val api: ApiClient,
)

/** An [ApiClient] on a mock engine with the production plugin set, each part optional. */
internal fun testHttp(
    localeProvider: LocaleProvider? = null,
    serverTime: ServerTimeSink? = null,
    hostAuth: HostAuth? = null,
    logger: ((String) -> Unit)? = null,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): TestHttp {
    val engine = MockEngine(handler)
    val api =
        ApiClient(
            baseUrl = TEST_BASE_URL,
            client =
                HttpClient(engine) {
                    configurePlugins(
                        enableLogging = logger != null,
                        logger = logger ?: {},
                        localeProvider = localeProvider,
                        serverTime = serverTime,
                    )
                    useBaseUrl(TEST_BASE_URL)
                    hostAuth?.let { installHostAuth(it) }
                },
        )
    return TestHttp(engine, api)
}
