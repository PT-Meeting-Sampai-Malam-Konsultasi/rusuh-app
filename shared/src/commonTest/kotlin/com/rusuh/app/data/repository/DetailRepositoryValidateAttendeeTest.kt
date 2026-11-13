package com.rusuh.app.data.repository

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.ApiClient.Companion.configure
import com.rusuh.app.data.remote.BookingApiService
import com.rusuh.app.data.remote.EventApiService
import com.rusuh.app.data.storage.InMemoryTokenStore
import com.rusuh.app.domain.model.AttendeeValidation
import com.rusuh.app.feature.auth.FakeAccountEmails
import com.rusuh.app.feature.auth.FakeAuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DetailRepositoryValidateAttendeeTest {
    private val problemHeaders = headersOf(HttpHeaders.ContentType, "application/problem+json")

    private suspend fun repository(
        status: HttpStatusCode,
        body: String,
    ): DetailRepositoryImpl {
        val api =
            ApiClient(
                baseUrl = BASE_URL,
                client = HttpClient(MockEngine { respond(body, status, problemHeaders) }) { configure(BASE_URL) },
            )
        return DetailRepositoryImpl(
            api = BookingApiService(api, InMemoryTokenStore().apply { setAdmissionToken("adm_test") }),
            serverClock = TestServerClock(),
            events = EventApiService(api),
            auth = FakeAuthRepository(),
            accountEmails = FakeAccountEmails(),
        )
    }

    private fun problem(
        status: Int,
        code: String,
    ) = """{"type":"https://api.rusuh.id/problems/x","title":"t","status":$status,"code":"$code"}"""

    private suspend fun validate(
        status: HttpStatusCode,
        code: String,
    ) = repository(status, problem(status.value, code)).validateAttendee("evt_1", 0, "Budi Santoso", "3174010101900001")

    @Test
    fun validationFailed400IsAnInvalidNik() =
        runTest {
            assertEquals(AttendeeValidation.NikInvalid, validate(HttpStatusCode.BadRequest, "VALIDATION_FAILED"))
        }

    @Test
    fun nikInvalid422IsAnInvalidNik() =
        runTest {
            assertEquals(AttendeeValidation.NikInvalid, validate(HttpStatusCode.UnprocessableEntity, "NIK_INVALID"))
        }

    @Test
    fun seatTaken409IsMapped() =
        runTest {
            assertEquals(AttendeeValidation.NikSeatTaken, validate(HttpStatusCode.Conflict, "NIK_SEAT_TAKEN"))
        }

    @Test
    fun dukcapilDown503IsUnavailable() =
        runTest {
            assertEquals(
                AttendeeValidation.DukcapilUnavailable,
                validate(HttpStatusCode.ServiceUnavailable, "DUKCAPIL_UNAVAILABLE"),
            )
        }

    @Test
    fun invalidAdmission403GoesBackToTheQueue() =
        runTest {
            assertEquals(AttendeeValidation.AdmissionInvalid, validate(HttpStatusCode.Forbidden, "ADMISSION_INVALID"))
        }

    @Test
    fun rateLimited429CarriesADelay() =
        runTest {
            assertIs<AttendeeValidation.RateLimited>(validate(HttpStatusCode.TooManyRequests, "RATE_LIMITED"))
        }

    @Test
    fun successReturnsTheValidationToken() =
        runTest {
            val ok = """{"validationToken":"av_1","nikMasked":"************0001","expiresAt":"2026-11-14T10:45:00Z"}"""
            val result =
                repository(HttpStatusCode.OK, ok).validateAttendee("evt_1", 0, "Budi Santoso", "3174010101900001")
            assertEquals(AttendeeValidation.Valid("av_1"), result)
        }

    private companion object {
        const val BASE_URL = "https://api.test"
    }
}
