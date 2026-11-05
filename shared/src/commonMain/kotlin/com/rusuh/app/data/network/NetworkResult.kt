package com.rusuh.app.data.network

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()

    data class Error(
        val message: String,
        val code: Int? = null,
        val cause: Throwable? = null,
    ) : NetworkResult<Nothing>()

    data object Loading : NetworkResult<Nothing>()
}
