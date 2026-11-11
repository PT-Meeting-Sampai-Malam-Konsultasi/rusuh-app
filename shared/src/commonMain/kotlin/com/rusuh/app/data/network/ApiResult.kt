package com.rusuh.app.data.network

import com.rusuh.app.data.remote.dto.ProblemDetails

/**
 * Transport outcome of one HTTP call. Unlike a body-only type it keeps the HTTP status,
 * so repositories can branch on 401/404/409/410/... and tell them apart from a timeout.
 */
sealed class ApiResult<out T> {
    /** 2xx response; [data] is the decoded body (the payload itself, no envelope). */
    data class Success<out T>(val code: Int, val data: T) : ApiResult<T>()

    /**
     * Non-2xx response (3xx such as 304 included). [problem] is null when the body was not a readable
     * `application/problem+json`. [retryAfterSeconds] comes from the `Retry-After` header (seconds form only).
     */
    data class Error(
        val code: Int,
        val problem: ProblemDetails? = null,
        val retryAfterSeconds: Int? = null,
    ) : ApiResult<Nothing>()

    /** The call failed before a usable response: timeout, I/O failure or unreadable success body. */
    data class Failure(val throwable: Throwable) : ApiResult<Nothing>()
}
