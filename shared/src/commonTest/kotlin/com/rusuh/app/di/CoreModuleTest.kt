package com.rusuh.app.di

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiClient.Companion.configurePlugins
import com.rusuh.app.data.network.ApiConfig
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.data.network.platformMockBaseUrl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.test.runTest
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class CoreModuleTest {
    private fun koin(
        environment: AppEnvironment,
        mockEngine: MockEngine? = null,
    ): Koin =
        koinApplication {
            allowOverride(true)
            modules(coreModule(), module { single { environment } })
            if (mockEngine != null) {
                modules(module { single<HttpClient> { HttpClient(mockEngine) { configurePlugins() } } })
            }
        }.koin

    @Test
    fun mockEnvironmentResolvesThreeClientsOnThePlatformMockUrl() {
        val koin = koin(AppEnvironment.Mock)
        assertEquals(platformMockBaseUrl, koin.get<ApiClient>(ApiClientQualifier.Api).baseUrl)
        assertEquals(platformMockBaseUrl, koin.get<ApiClient>(ApiClientQualifier.Queue).baseUrl)
        assertEquals(platformMockBaseUrl, koin.get<ApiClient>(ApiClientQualifier.Pay).baseUrl)
    }

    @Test
    fun productionResolvesDistinctClientPerHost() {
        val koin = koin(AppEnvironment.Production)
        val config = koin.get<ApiConfig>()
        assertEquals(config.baseUrl, koin.get<ApiClient>(ApiClientQualifier.Api).baseUrl)
        assertEquals(config.queueBaseUrl, koin.get<ApiClient>(ApiClientQualifier.Queue).baseUrl)
        assertEquals(config.payBaseUrl, koin.get<ApiClient>(ApiClientQualifier.Pay).baseUrl)
        assertEquals(ApiConfig.PLACEHOLDER_PROD_QUEUE_URL, koin.get<ApiClient>(ApiClientQualifier.Queue).baseUrl)
    }

    @Test
    fun clientsAreSingletons() {
        val koin = koin(AppEnvironment.Staging)
        assertSame(koin.get<ApiClient>(ApiClientQualifier.Pay), koin.get<ApiClient>(ApiClientQualifier.Pay))
        assertSame(koin.get<HttpClient>(), koin.get<HttpClient>())
    }

    @Test
    fun payClientRequestHitsThePayHost() =
        runTest {
            val urls = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    urls += request.url.toString()
                    respondOk("")
                }
            val koin = koin(AppEnvironment.Production, engine)

            val result = koin.get<ApiClient>(ApiClientQualifier.Pay).get<Unit>("/payments/1")

            assertIs<ApiResult.Success<Unit>>(result)
            assertEquals(listOf("${ApiConfig.PLACEHOLDER_PROD_PAY_URL}/payments/1"), urls)
        }

    @Test
    fun eachHostClientHitsItsOwnHost() =
        runTest {
            val urls = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    urls += request.url.toString()
                    respondOk("")
                }
            val koin = koin(AppEnvironment.Staging, engine)

            koin.get<ApiClient>(ApiClientQualifier.Api).get<Unit>("/a")
            koin.get<ApiClient>(ApiClientQualifier.Queue).get<Unit>("/b")
            koin.get<ApiClient>(ApiClientQualifier.Pay).get<Unit>("/c")

            assertEquals(
                listOf(
                    "${ApiConfig.PLACEHOLDER_STAGING_API_URL}/a",
                    "${ApiConfig.PLACEHOLDER_STAGING_QUEUE_URL}/b",
                    "${ApiConfig.PLACEHOLDER_STAGING_PAY_URL}/c",
                ),
                urls,
            )
        }
}
