package com.rusuh.app.data.repository

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiClient.Companion.configure
import com.rusuh.app.data.remote.BookingApiService
import com.rusuh.app.data.remote.EventApiService
import com.rusuh.app.data.storage.InMemoryTokenStore
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.feature.auth.FakeAccountEmails
import com.rusuh.app.feature.auth.FakeAuthRepository
import com.rusuh.app.feature.auth.TEST_EMAIL
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DetailRepositoryImplTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val categories =
        """{"eventId":"evt_1","categories":[],"maxTicketsPerOrder":3,"maxTickets":3,""" +
            """"adminFee":{"amount":10000,"currency":"IDR"},"expiresAt":"2026-11-14T10:45:00Z",""" +
            """"serverNow":"2026-11-14T10:40:00Z"}"""
    private val event =
        """{"eventId":"evt_1","name":"Way Back Home","subtitle":"Konser tunggal",""" +
            """"venue":"Beach City","city":"Jakarta"}"""

    private suspend fun repository(
        auth: FakeAuthRepository,
        emails: FakeAccountEmails = FakeAccountEmails(),
        eventStatus: HttpStatusCode = HttpStatusCode.OK,
    ): DetailRepositoryImpl {
        val api =
            ApiClient(
                baseUrl = BASE_URL,
                client =
                    HttpClient(
                        MockEngine { request ->
                            if (request.url.encodedPath.endsWith("/categories")) {
                                respond(categories, HttpStatusCode.OK, jsonHeaders)
                            } else {
                                respond(event, eventStatus, jsonHeaders)
                            }
                        },
                    ) { configure(BASE_URL) },
            )
        return DetailRepositoryImpl(
            api = BookingApiService(api, InMemoryTokenStore().apply { setAdmissionToken("adm_test") }),
            serverClock = TestServerClock(),
            events = EventApiService(api),
            auth = auth,
            accountEmails = emails,
        )
    }

    @Test
    fun usesTheEmailSavedAtSignInWithoutAskingTheProfile() =
        runTest {
            val auth = FakeAuthRepository()
            val emails = FakeAccountEmails().apply { stored = "saved@example.test" }

            val offering = repository(auth, emails).getDetail("evt_1")

            assertEquals("saved@example.test", offering.profile.email)
            assertEquals(0, auth.meCalls)
        }

    @Test
    fun readsTheProfileOnceWhenNoEmailIsSavedAndKeepsIt() =
        runTest {
            val auth = FakeAuthRepository()
            val emails = FakeAccountEmails()

            val offering = repository(auth, emails).getDetail("evt_1")

            assertEquals(TEST_EMAIL, offering.profile.email)
            assertEquals(TEST_EMAIL, emails.stored)
            assertEquals("Way Back Home", offering.event.title)
            assertEquals("Konser tunggal", offering.event.subtitle)
            assertEquals("Beach City, Jakarta", offering.event.meta)
        }

    @Test
    fun aFailedProfileReadLeavesTheEmailEmptyAndDoesNotBlockThePage() =
        runTest {
            val auth = FakeAuthRepository().apply { onMe = { DomainResult.NetworkError } }

            val offering = repository(auth).getDetail("evt_1")

            assertEquals("", offering.profile.email)
            assertEquals("Way Back Home", offering.event.title)
        }

    @Test
    fun aFailedEventReadKeepsAGenericTitle() =
        runTest {
            val offering =
                repository(
                    FakeAuthRepository(),
                    eventStatus = HttpStatusCode.InternalServerError,
                ).getDetail("evt_1")

            assertEquals("Event", offering.event.title)
            assertEquals(TEST_EMAIL, offering.profile.email)
        }

    private companion object {
        const val BASE_URL = "https://api.test"
    }
}
