package com.rusuh.app.data.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException

actual fun isNetworkFailure(t: Throwable): Boolean =
    t is IOException ||
        t is HttpRequestTimeoutException ||
        t is ConnectTimeoutException ||
        t is SocketTimeoutException
