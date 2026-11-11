package com.rusuh.app.data.repository

import com.rusuh.app.domain.model.DomainException
import com.rusuh.app.domain.model.DomainResult

private const val HTTP_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500

/**
 * True for a failure that a later attempt can fix: the network (offline, timeout), 408, 429 and 5xx. Everything else
 * (401, 403, 404, other 4xx, an unreadable body) will not change by waiting.
 */
internal fun DomainException.isTransient(): Boolean =
    when (val failure = result) {
        DomainResult.NetworkError -> true
        is DomainResult.Error ->
            failure.code == HTTP_TIMEOUT || failure.code == HTTP_TOO_MANY_REQUESTS || failure.code >= HTTP_SERVER_ERROR
        else -> false
    }

/** The server's `Retry-After` in seconds, if the failure carried one. */
internal fun DomainException.retryAfterSeconds(): Int? = (result as? DomainResult.Error)?.retryAfterSeconds
