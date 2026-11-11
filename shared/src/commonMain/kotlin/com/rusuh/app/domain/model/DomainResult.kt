package com.rusuh.app.domain.model

/** Outcome the UI layer consumes. Repositories map transport results into this type. */
sealed class DomainResult<out T> {
    data class Success<out T>(val data: T) : DomainResult<T>()

    /**
     * The server rejected the call. [code] is the HTTP status, [errorCode] the stable machine code
     * and [message] the server-provided text, if any. The UI picks copy from [errorCode].
     */
    data class Error(
        val message: String? = null,
        val code: Int = 0,
        val errorCode: ErrorCode = ErrorCode.UNKNOWN,
    ) : DomainResult<Nothing>()

    /** The call succeeded but there is nothing to show. */
    data object Empty : DomainResult<Nothing>()

    /** The server could not be reached (offline, timeout, connection failure). */
    data object NetworkError : DomainResult<Nothing>()

    /** Anything else that went wrong on the client, such as an unreadable response body. */
    data object TechnicalError : DomainResult<Nothing>()
}
