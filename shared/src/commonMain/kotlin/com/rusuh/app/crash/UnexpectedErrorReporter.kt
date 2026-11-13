package com.rusuh.app.crash

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import kotlin.concurrent.Volatile

/**
 * Reports unexpected errors to the crash reporter. Only TechnicalError results and Error results
 * with UNKNOWN or INTERNAL_ERROR codes are reported; normal business errors are ignored.
 */
object UnexpectedErrorReporter {
    @Volatile
    private var reporter: CrashReporter = NoOpCrashReporter

    fun install(crashReporter: CrashReporter) {
        reporter = crashReporter
    }

    fun <T> reportIfUnexpected(result: DomainResult<T>): DomainResult<T> {
        when (result) {
            is DomainResult.TechnicalError -> {
                reporter.recordNonFatal(RuntimeException("TechnicalError in data layer"))
            }
            is DomainResult.Error -> {
                if (result.errorCode == ErrorCode.UNKNOWN || result.errorCode == ErrorCode.INTERNAL_ERROR) {
                    val msg =
                        "Unexpected error: code=${result.code}, " +
                            "errorCode=${result.errorCode}, traceId=${result.traceId}"
                    reporter.recordNonFatal(RuntimeException(msg))
                }
            }
            else -> Unit
        }
        return result
    }
}
