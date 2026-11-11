package com.rusuh.app.data.repository

import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.remote.dto.ProblemDetails
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiResultMapperTest {
    @Test
    fun `success maps to Success with the same data`() {
        assertEquals(DomainResult.Success("x"), ApiResult.Success(200, "x").toDomainResult())
    }

    @Test
    fun `204 Unit success maps to Success Unit`() {
        assertEquals(DomainResult.Success(Unit), ApiResult.Success(204, Unit).toDomainResult())
    }

    @Test
    fun `transform result is used and null becomes Empty`() {
        assertEquals(DomainResult.Success(3), ApiResult.Success(200, "abc").toDomainResult { it.length })
        assertEquals(DomainResult.Empty, ApiResult.Success(200, "").toDomainResult { it.ifEmpty { null } })
    }

    @Test
    fun `error carries status, machine code and server message`() {
        val problem = ProblemDetails(title = "Judul", detail = "Rincian", status = 409, code = ErrorCode.NIK_SEAT_TAKEN)

        val result = ApiResult.Error(409, problem).toDomainResult()

        assertEquals(DomainResult.Error("Rincian", 409, ErrorCode.NIK_SEAT_TAKEN), result)
    }

    @Test
    fun `error message falls back to the title`() {
        val problem = ProblemDetails(title = "Judul", code = ErrorCode.FORBIDDEN)

        assertEquals(
            DomainResult.Error("Judul", 403, ErrorCode.FORBIDDEN),
            ApiResult.Error(403, problem).toDomainResult(),
        )
    }

    @Test
    fun `error without a problem body maps to UNKNOWN with the status`() {
        assertEquals(DomainResult.Error(null, 502, ErrorCode.UNKNOWN), ApiResult.Error(502).toDomainResult())
    }

    @Test
    fun `io failures and timeouts map to NetworkError`() {
        assertEquals(DomainResult.NetworkError, ApiResult.Exception(IOException("offline")).toDomainResult())
        val timeout = HttpRequestTimeoutException(HttpRequestBuilder())
        assertEquals(DomainResult.NetworkError, ApiResult.Exception(timeout).toDomainResult())
    }

    @Test
    fun `other exceptions map to TechnicalError`() {
        assertEquals(DomainResult.TechnicalError, ApiResult.Exception(SerializationException("bad")).toDomainResult())
        assertEquals(DomainResult.TechnicalError, ApiResult.Exception(IllegalStateException()).toDomainResult())
    }
}
