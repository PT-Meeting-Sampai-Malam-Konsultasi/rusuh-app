package com.rusuh.app.data.repository

import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.isNetworkFailure
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode

/** Maps a transport result to a [DomainResult], keeping the payload as is. */
fun <T> ApiResult<T>.toDomainResult(): DomainResult<T> = toDomainResult { it }

/**
 * Maps a transport result to a [DomainResult], converting the payload with [transform].
 * A null transform result becomes [DomainResult.Empty].
 */
fun <T, R> ApiResult<T>.toDomainResult(transform: (T) -> R?): DomainResult<R> =
    when (this) {
        is ApiResult.Success -> transform(data)?.let { DomainResult.Success(it) } ?: DomainResult.Empty
        is ApiResult.Error ->
            DomainResult.Error(
                message = problem?.detail ?: problem?.title,
                code = code,
                errorCode = problem?.code ?: ErrorCode.UNKNOWN,
                traceId = problem?.traceId,
                extras = problem?.extras.orEmpty(),
                retryAfterSeconds = retryAfterSeconds,
            )
        is ApiResult.Failure ->
            if (isNetworkFailure(throwable)) DomainResult.NetworkError else DomainResult.TechnicalError
    }
