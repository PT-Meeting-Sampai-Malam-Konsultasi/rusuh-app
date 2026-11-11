package com.rusuh.app.data.network.plugin

import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.AppLanguage
import com.rusuh.app.data.network.HostAuth
import com.rusuh.app.data.network.idempotencyKey
import com.rusuh.app.data.network.newIdempotencyKey
import com.rusuh.app.data.storage.FakeSecureStore
import com.rusuh.app.data.storage.SecureTokenStore
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeaderPluginsTest {
    private fun HttpRequestData.header(name: String): String? = headers[name]

    @Test
    fun acceptLanguageFollowsTheProviderOnEveryRequest() =
        runTest {
            var language = AppLanguage.Indonesian
            val http = testHttp(localeProvider = { language }) { respondOk("") }

            http.api.get<Unit>("/a")
            language = AppLanguage.English
            http.api.get<Unit>("/b")

            assertEquals(listOf("id", "en"), http.engine.requestHistory.map { it.header("Accept-Language") })
        }

    @Test
    fun explicitAcceptLanguageOfTheCallIsKept() =
        runTest {
            val http = testHttp(localeProvider = { AppLanguage.Indonesian }) { respondOk("") }

            http.api.get<Unit>("/a") { headers.append("Accept-Language", "en") }

            assertEquals("en", http.engine.requestHistory.single().header("Accept-Language"))
        }

    @Test
    fun deviceLanguageCodesMapToSupportedLanguages() {
        assertEquals(AppLanguage.English, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.English, AppLanguage.fromCode("en-US"))
        assertEquals(AppLanguage.English, AppLanguage.fromCode("EN_gb"))
        assertEquals(AppLanguage.Indonesian, AppLanguage.fromCode("id"))
        assertEquals(AppLanguage.Indonesian, AppLanguage.fromCode("in"))
        assertEquals(AppLanguage.Indonesian, AppLanguage.fromCode("id-ID"))
        assertEquals(AppLanguage.Indonesian, AppLanguage.fromCode("fr"))
        assertEquals(AppLanguage.Indonesian, AppLanguage.fromCode(null))
    }

    @Test
    fun tokensAreSentWhenPresentAndOmittedWhenAbsent() =
        runTest {
            val store = SecureTokenStore(FakeSecureStore())
            val http = testHttp(hostAuth = HostAuth(store)) { respondOk("") }

            http.api.get<Unit>("/anonymous")
            store.setDeviceToken("device-1")
            http.api.get<Unit>("/device-only")
            store.accessToken = "access-1"
            http.api.get<Unit>("/signed-in")

            val (anonymous, deviceOnly, signedIn) = http.engine.requestHistory
            assertNull(anonymous.header("X-Device-Token"))
            assertNull(anonymous.header("Authorization"))
            assertEquals("device-1", deviceOnly.header("X-Device-Token"))
            assertNull(deviceOnly.header("Authorization"))
            assertEquals("device-1", signedIn.header("X-Device-Token"))
            assertEquals("Bearer access-1", signedIn.header("Authorization"))
        }

    @Test
    fun queueAndAdmissionTokensAreNeverAutomatic() =
        runTest {
            val store = SecureTokenStore(FakeSecureStore())
            store.setQueueToken("evt", "queue-1")
            store.setAdmissionToken("admission-1")
            val http = testHttp(hostAuth = HostAuth(store)) { respondOk("") }

            http.api.get<Unit>("/plain")
            http.api.get<Unit>("/opted-in") {
                queueToken("queue-1")
                admissionToken("admission-1")
            }

            val (plain, optedIn) = http.engine.requestHistory
            assertNull(plain.header("X-Queue-Token"))
            assertNull(plain.header("X-Admission-Token"))
            assertEquals("queue-1", optedIn.header("X-Queue-Token"))
            assertEquals("admission-1", optedIn.header("X-Admission-Token"))
        }

    @Test
    fun hostPolicyFlagsDisableTheirHeader() =
        runTest {
            val store = SecureTokenStore(FakeSecureStore())
            store.setDeviceToken("device-1")
            store.accessToken = "access-1"
            val noBearer = testHttp(hostAuth = HostAuth(store, sendBearer = false)) { respondOk("") }
            val noDevice = testHttp(hostAuth = HostAuth(store, sendDeviceToken = false)) { respondOk("") }

            noBearer.api.get<Unit>("/x")
            noDevice.api.get<Unit>("/x")

            val first = noBearer.engine.requestHistory.single()
            assertEquals("device-1", first.header("X-Device-Token"))
            assertNull(first.header("Authorization"))
            val second = noDevice.engine.requestHistory.single()
            assertNull(second.header("X-Device-Token"))
            assertEquals("Bearer access-1", second.header("Authorization"))
        }

    @Test
    fun skipSessionAuthKeepsTheDeviceTokenButDropsBearer() =
        runTest {
            val store = SecureTokenStore(FakeSecureStore())
            store.setDeviceToken("device-1")
            store.accessToken = "access-1"
            val http = testHttp(hostAuth = HostAuth(store)) { respondOk("") }

            http.api.post<Unit>("/auth/refresh") { skipSessionAuth() }

            val request = http.engine.requestHistory.single()
            assertEquals("device-1", request.header("X-Device-Token"))
            assertNull(request.header("Authorization"))
        }

    @Test
    fun hostWithoutAuthSendsNoTokens() =
        runTest {
            val http = testHttp { respondOk("") }

            http.api.get<Unit>("/x")

            assertNull(http.engine.requestHistory.single().header("X-Device-Token"))
        }

    @Test
    fun loggingMasksTokenHeadersAtHeadersLevelCaseInsensitively() =
        runTest {
            val store = SecureTokenStore(FakeSecureStore())
            store.setDeviceToken("device-secret")
            store.accessToken = "access-secret"
            val lines = mutableListOf<String>()
            val http =
                testHttp(
                    localeProvider = { AppLanguage.English },
                    hostAuth = HostAuth(store),
                    logging = LogLevel.HEADERS to { lines += it },
                ) {
                    respond(
                        "",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.SetCookie, "__Host-rt=cookie-secret; Secure"),
                    )
                }

            http.api.get<Unit>("/x") {
                queueToken("queue-secret")
                admissionToken("admission-secret")
                headers.append("x-turnstile-token", "turnstile-secret")
                headers.append("COOKIE", "pay-cookie-secret")
            }

            val log = lines.joinToString("\n")
            assertTrue("Accept-Language" in log, "headers must be logged at this level")
            listOf(
                "device-secret",
                "access-secret",
                "queue-secret",
                "admission-secret",
                "turnstile-secret",
                "pay-cookie-secret",
                "cookie-secret",
            ).forEach { assertTrue(it !in log, "log leaked $it") }
        }

    @Test
    fun redirectsAreNotFollowedSoTokensStayOnTheirHost() =
        runTest {
            val store = SecureTokenStore(FakeSecureStore())
            store.setDeviceToken("device-1")
            store.accessToken = "access-1"
            val http =
                testHttp(hostAuth = HostAuth(store)) {
                    respond(
                        "",
                        HttpStatusCode.Found,
                        headersOf(HttpHeaders.Location, "https://evil.test/steal"),
                    )
                }

            val result = http.api.get<Unit>("/x")

            assertEquals(302, assertIs<ApiResult.Error>(result).code)
            assertEquals(1, http.engine.requestHistory.size)
        }

    @Test
    fun idempotencyKeysAreUniqueUuidsAndSentVerbatim() =
        runTest {
            val first = newIdempotencyKey()
            val second = newIdempotencyKey()
            val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
            assertTrue(uuid.matches(first), first)
            assertNotEquals(first, second)

            val http = testHttp { respondOk("") }
            http.api.post<Unit>("/bookings") { idempotencyKey(first) }
            http.api.post<Unit>("/bookings") { idempotencyKey(first) }

            assertEquals(listOf(first, first), http.engine.requestHistory.map { it.header("Idempotency-Key") })
        }

    @Test
    fun blankIdempotencyKeyIsRejectedAsAnErrorResult() =
        runTest {
            val http = testHttp { respondOk("") }

            val result = http.api.post<Unit>("/bookings") { idempotencyKey(" ") }

            val failure = assertIs<ApiResult.Failure>(result)
            assertIs<IllegalArgumentException>(failure.throwable)
            assertFailsWith<IllegalArgumentException> { newBlank() }
        }

    private fun newBlank() = io.ktor.client.request.HttpRequestBuilder().idempotencyKey("")
}
