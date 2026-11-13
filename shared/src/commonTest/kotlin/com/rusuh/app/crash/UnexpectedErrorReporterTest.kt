package com.rusuh.app.crash

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnexpectedErrorReporterTest {
    private val reporter = FakeCrashReporter()

    init {
        UnexpectedErrorReporter.install(reporter)
    }

    @AfterTest
    fun tearDown() {
        reporter.clear()
        UnexpectedErrorReporter.install(NoOpCrashReporter)
    }

    @Test
    fun `reports TechnicalError`() {
        UnexpectedErrorReporter.reportIfUnexpected(DomainResult.TechnicalError)
        assertEquals(1, reporter.nonFatals.size)
        assertTrue(reporter.nonFatals[0].message!!.contains("TechnicalError"))
    }

    @Test
    fun `reports Error with UNKNOWN code`() {
        val error = DomainResult.Error(code = 500, errorCode = ErrorCode.UNKNOWN, traceId = "trace-123")
        UnexpectedErrorReporter.reportIfUnexpected(error)
        assertEquals(1, reporter.nonFatals.size)
        assertTrue(reporter.nonFatals[0].message!!.contains("UNKNOWN"))
        assertTrue(reporter.nonFatals[0].message!!.contains("trace-123"))
    }

    @Test
    fun `reports Error with INTERNAL_ERROR code`() {
        val error = DomainResult.Error(code = 500, errorCode = ErrorCode.INTERNAL_ERROR)
        UnexpectedErrorReporter.reportIfUnexpected(error)
        assertEquals(1, reporter.nonFatals.size)
        assertTrue(reporter.nonFatals[0].message!!.contains("INTERNAL_ERROR"))
    }

    @Test
    fun `does not report business errors`() {
        val errors =
            listOf(
                ErrorCode.WRONG_CODE,
                ErrorCode.QUEUE_CLOSED,
                ErrorCode.NO_STOCK,
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.RATE_LIMITED,
            )
        errors.forEach { code ->
            val error = DomainResult.Error(code = 400, errorCode = code)
            UnexpectedErrorReporter.reportIfUnexpected(error)
        }
        assertTrue(reporter.nonFatals.isEmpty())
    }

    @Test
    fun `does not report Success`() {
        UnexpectedErrorReporter.reportIfUnexpected(DomainResult.Success("data"))
        assertTrue(reporter.nonFatals.isEmpty())
    }

    @Test
    fun `does not report NetworkError`() {
        UnexpectedErrorReporter.reportIfUnexpected(DomainResult.NetworkError)
        assertTrue(reporter.nonFatals.isEmpty())
    }

    @Test
    fun `does not report Empty`() {
        UnexpectedErrorReporter.reportIfUnexpected(DomainResult.Empty)
        assertTrue(reporter.nonFatals.isEmpty())
    }

    @Test
    fun `returns the same result`() {
        val success = DomainResult.Success("test")
        assertEquals(success, UnexpectedErrorReporter.reportIfUnexpected(success))

        val error = DomainResult.Error(code = 400, errorCode = ErrorCode.WRONG_CODE)
        assertEquals(error, UnexpectedErrorReporter.reportIfUnexpected(error))
    }
}
