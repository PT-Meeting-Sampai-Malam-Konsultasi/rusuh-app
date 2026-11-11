package com.rusuh.app.domain.model

import kotlinx.serialization.json.JsonElement

/** Outcome the UI layer consumes. Repositories map transport results into this type. */
sealed class DomainResult<out T> {
    data class Success<out T>(val data: T) : DomainResult<T>()

    /**
     * The server rejected the call. [code] is the HTTP status and [errorCode] the stable machine code;
     * the UI picks copy from [errorCode]. [message] is server text for diagnostics only, never shown as is.
     * [extras] holds extra problem members (e.g. `attemptsLeft`, `categoryId`, `nikMasked`),
     * [traceId] the server trace id and [retryAfterSeconds] the `Retry-After` hint.
     */
    data class Error(
        val message: String? = null,
        val code: Int = 0,
        val errorCode: ErrorCode = ErrorCode.UNKNOWN,
        val traceId: String? = null,
        val extras: Map<String, JsonElement> = emptyMap(),
        val retryAfterSeconds: Int? = null,
    ) : DomainResult<Nothing>()

    /** The call succeeded but there is nothing to show. */
    data object Empty : DomainResult<Nothing>()

    /** The server could not be reached (offline, timeout, connection failure). */
    data object NetworkError : DomainResult<Nothing>()

    /** Anything else that went wrong on the client, such as an unreadable response body. */
    data object TechnicalError : DomainResult<Nothing>()
}
