package com.rusuh.app.data.network.plugin

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.HostAuth
import com.rusuh.app.data.network.session.ApiTokenRefresher
import com.rusuh.app.data.network.session.RefreshOutcome
import com.rusuh.app.data.network.session.SessionEvent
import com.rusuh.app.data.network.session.SessionEvents
import com.rusuh.app.data.network.session.SessionRefreshCoordinator
import com.rusuh.app.data.network.session.TokenRefresher
import com.rusuh.app.data.remote.dto.RefreshRequestDto
import com.rusuh.app.data.storage.FakeSecureStore
import com.rusuh.app.data.storage.SecureTokenStore
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRefreshTest {
    private class Fixture(
        refresher: (suspend (String) -> RefreshOutcome)? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        val store = SecureTokenStore(FakeSecureStore())
        val events = SessionEvents()
        val refreshCalls = mutableListOf<String>()
        val http: TestHttp

        init {
            val tokenRefresher =
                lazy {
                    if (refresher != null) {
                        TokenRefresher { token ->
                            refreshCalls += token
                            refresher(token)
                        }
                    } else {
                        ApiTokenRefresher(http.api)
                    }
                }
            val coordinator = SessionRefreshCoordinator(store, tokenRefresher, events)
            http = testHttp(hostAuth = HostAuth(store, coordinator), handler = handler)
        }

        /** Subscribes now to the next session event; await it after the call under test. */
        fun nextEvent(scope: CoroutineScope): Deferred<SessionEvent> =
            scope.async(start = CoroutineStart.UNDISPATCHED) { events.events.first() }

        suspend fun signIn(
            access: String? = "old",
            refresh: String? = "refresh-1",
        ) {
            store.setDeviceToken("device-1")
            store.accessToken = access
            refresh?.let { store.setRefreshToken(it) }
        }
    }

    private fun MockRequestHandleScope.unauthorized() =
        respond(
            "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"code\":\"UNAUTHORIZED\"}",
            HttpStatusCode.Unauthorized,
            headersOf(HttpHeaders.ContentType, "application/problem+json"),
        )

    private fun MockRequestHandleScope.ok(body: String = "{}") =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.bearer(): String? = headers[HttpHeaders.Authorization]

    private fun TestHttp.paths() = engine.requestHistory.map { it.url.encodedPath }

    @Test
    fun unauthorizedRefreshesOnceAndRetriesWithTheNewToken() =
        runTest {
            val f =
                Fixture(refresher = { RefreshOutcome.Refreshed("new", "refresh-2") }) { request ->
                    if (request.bearer() == "Bearer new") ok() else unauthorized()
                }
            f.signIn()

            val result = f.http.api.get<Unit>("/tickets")

            assertIs<ApiResult.Success<Unit>>(result)
            assertEquals(listOf("Bearer old", "Bearer new"), f.http.engine.requestHistory.map { it.bearer() })
            assertEquals(listOf("refresh-1"), f.refreshCalls)
            assertEquals("new", f.store.accessToken)
            assertEquals("refresh-2", f.store.getRefreshToken())
        }

    @Test
    fun retryHappensOnlyOnceWhenTheNewTokenIsAlsoRejected() =
        runTest {
            val f = Fixture(refresher = { RefreshOutcome.Refreshed("new", "refresh-2") }) { unauthorized() }
            f.signIn()

            val result = f.http.api.get<Unit>("/tickets")

            assertEquals(401, assertIs<ApiResult.Error>(result).code)
            assertEquals(2, f.http.engine.requestHistory.size)
            assertEquals(1, f.refreshCalls.size)
        }

    @Test
    fun rejectedRefreshExpiresTheSessionAndClearsTokens() =
        runTest {
            val f = Fixture(refresher = { RefreshOutcome.Rejected }) { unauthorized() }
            f.signIn()
            f.store.setAdmissionToken("admission-1")
            val expired = f.nextEvent(backgroundScope)

            val result = f.http.api.get<Unit>("/tickets")

            assertEquals(401, assertIs<ApiResult.Error>(result).code)
            assertEquals(1, f.http.engine.requestHistory.size)
            assertEquals(SessionEvent.SessionExpired, expired.await())
            assertNull(f.store.accessToken)
            assertNull(f.store.getRefreshToken())
            assertNull(f.store.getAdmissionToken())
            assertEquals("device-1", f.store.getDeviceToken())
        }

    @Test
    fun refreshThatCannotCompleteKeepsTheSession() =
        runTest {
            val f = Fixture(refresher = { RefreshOutcome.Unavailable }) { unauthorized() }
            f.signIn()
            val expired = f.nextEvent(backgroundScope)

            val result = f.http.api.get<Unit>("/tickets")

            assertIs<ApiResult.Error>(result)
            assertNull(withContext(Dispatchers.Default) { withTimeoutOrNull(QUIET_MS) { expired.await() } })
            assertEquals("old", f.store.accessToken)
            assertEquals("refresh-1", f.store.getRefreshToken())
        }

    @Test
    fun throwingRefresherKeepsTheSession() =
        runTest {
            val f = Fixture(refresher = { error("boom") }) { unauthorized() }
            f.signIn()

            val result = f.http.api.get<Unit>("/tickets")

            assertIs<ApiResult.Error>(result)
            assertEquals("refresh-1", f.store.getRefreshToken())
        }

    @Test
    fun anonymousUnauthorizedIsLeftAlone() =
        runTest {
            val f = Fixture(refresher = { RefreshOutcome.Refreshed("new", null) }) { unauthorized() }
            val expired = f.nextEvent(backgroundScope)

            val result = f.http.api.get<Unit>("/tickets")

            assertIs<ApiResult.Error>(result)
            assertEquals(1, f.http.engine.requestHistory.size)
            assertTrue(f.refreshCalls.isEmpty())
            assertNull(withContext(Dispatchers.Default) { withTimeoutOrNull(QUIET_MS) { expired.await() } })
        }

    @Test
    fun accessTokenLostOnRestartIsRestoredFromTheRefreshToken() =
        runTest {
            val f =
                Fixture(refresher = { RefreshOutcome.Refreshed("new", null) }) { request ->
                    if (request.bearer() == "Bearer new") ok() else unauthorized()
                }
            f.signIn(access = null)

            val result = f.http.api.get<Unit>("/tickets")

            assertIs<ApiResult.Success<Unit>>(result)
            assertEquals(listOf(null, "Bearer new"), f.http.engine.requestHistory.map { it.bearer() })
            assertEquals("refresh-1", f.store.getRefreshToken())
        }

    @Test
    fun missingRefreshTokenWithASessionExpiresIt() =
        runTest {
            val f = Fixture(refresher = { RefreshOutcome.Refreshed("new", null) }) { unauthorized() }
            f.signIn(refresh = null)
            val expired = f.nextEvent(backgroundScope)

            f.http.api.get<Unit>("/tickets")

            assertEquals(SessionEvent.SessionExpired, expired.await())
            assertTrue(f.refreshCalls.isEmpty())
            assertNull(f.store.accessToken)
        }

    @Test
    fun concurrentUnauthorizedResponsesShareOneRefresh() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val unauthorizedSeen = Channel<Unit>(Channel.UNLIMITED)
            val f =
                Fixture(refresher = {
                    gate.await()
                    RefreshOutcome.Refreshed("new", "refresh-2")
                }) { request ->
                    if (request.bearer() == "Bearer new") {
                        ok()
                    } else {
                        unauthorizedSeen.trySend(Unit)
                        unauthorized()
                    }
                }
            f.signIn()
            val calls = 5

            val results = async { (1..calls).map { async { f.http.api.get<Unit>("/tickets/$it") } }.awaitAll() }
            withContext(Dispatchers.Default) {
                withTimeout(WAIT_MS) { repeat(calls) { unauthorizedSeen.receive() } }
            }
            gate.complete(Unit)

            results.await().forEach { assertIs<ApiResult.Success<Unit>>(it) }
            assertEquals(1, f.refreshCalls.size)
            assertEquals(calls * 2, f.http.engine.requestHistory.size)
        }

    @Test
    fun lateConcurrentUnauthorizedReusesTheFreshTokenWithoutAnotherRefresh() =
        runTest {
            val f =
                Fixture(refresher = { RefreshOutcome.Refreshed("new", "refresh-2") }) { request ->
                    if (request.bearer() == "Bearer new") ok() else unauthorized()
                }
            f.signIn()
            // A request that was built with the old token and only now gets its 401.
            f.http.api.get<Unit>("/first")
            val late = f.http.api.get<Unit>("/second") { headers.append(HttpHeaders.Authorization, "Bearer old") }

            assertIs<ApiResult.Success<Unit>>(late)
            assertEquals(1, f.refreshCalls.size)
        }

    @Test
    fun refreshEndpointThatReturns401DoesNotLoopAndExpiresTheSession() =
        runTest {
            val f =
                Fixture { unauthorized() }
            f.signIn()
            val expired = f.nextEvent(backgroundScope)

            val result = f.http.api.get<Unit>("/tickets")

            assertEquals(401, assertIs<ApiResult.Error>(result).code)
            assertEquals(listOf("/tickets", "/auth/refresh"), f.http.paths())
            assertEquals(SessionEvent.SessionExpired, expired.await())
            assertNull(f.store.getRefreshToken())
        }

    @Test
    fun defaultRefresherSendsTheRefreshTokenInTheBodyWithoutBearer() =
        runTest {
            var refreshRequest: HttpRequestData? = null
            val f =
                Fixture { request ->
                    when {
                        request.url.encodedPath.endsWith("/auth/refresh") -> {
                            refreshRequest = request
                            ok("{\"accessToken\":\"new\",\"refreshToken\":\"refresh-2\",\"expiresIn\":900}")
                        }
                        request.bearer() == "Bearer new" -> ok()
                        else -> unauthorized()
                    }
                }
            f.signIn()

            val result = f.http.api.get<Unit>("/tickets")

            assertIs<ApiResult.Success<Unit>>(result)
            val refresh = refreshRequest!!
            assertNull(refresh.bearer())
            assertEquals("device-1", refresh.headers["X-Device-Token"])
            val body = (refresh.body as TextContent).text
            assertEquals(
                ApiClient.defaultJson.encodeToString(RefreshRequestDto.serializer(), RefreshRequestDto("refresh-1")),
                body,
            )
            assertEquals("refresh-2", f.store.getRefreshToken())
            assertEquals("new", f.store.accessToken)
        }

    @Test
    fun defaultRefresherTreatsServerErrorsAsUnavailable() =
        runTest {
            val f =
                Fixture { request ->
                    if (request.url.encodedPath.endsWith("/auth/refresh")) {
                        respond("", HttpStatusCode.ServiceUnavailable)
                    } else {
                        unauthorized()
                    }
                }
            f.signIn()

            f.http.api.get<Unit>("/tickets")

            assertEquals("refresh-1", f.store.getRefreshToken())
            assertEquals("old", f.store.accessToken)
        }

    @Test
    fun refreshWriteLostToALogoutDoesNotResurrectTheSession() =
        runTest {
            lateinit var f: Fixture
            f =
                Fixture(refresher = {
                    f.store.clearSession()
                    RefreshOutcome.Refreshed("new", "refresh-2")
                }) { unauthorized() }
            f.signIn()

            f.http.api.get<Unit>("/tickets")

            assertNull(f.store.accessToken)
            assertNull(f.store.getRefreshToken())
        }

    @Test
    fun noStateLeaksIntoTokenFreeDtos() {
        assertTrue("refresh-1" !in RefreshRequestDto("refresh-1").toString())
        assertTrue("secret" !in RefreshOutcome.Refreshed("new-secret", "r-secret").toString())
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val QUIET_MS = 200L
    }
}
