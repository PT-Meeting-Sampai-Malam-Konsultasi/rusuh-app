package com.rusuh.app.data.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkFailureTest {
    @Test
    fun `io failures and timeouts are network failures`() {
        assertTrue(isNetworkFailure(IOException("offline")))
        assertTrue(isNetworkFailure(HttpRequestTimeoutException(HttpRequestBuilder())))
        assertTrue(isNetworkFailure(ConnectTimeoutException("connect", null)))
        assertTrue(isNetworkFailure(SocketTimeoutException("socket")))
    }

    @Test
    fun `parsing and programming errors are not network failures`() {
        assertFalse(isNetworkFailure(SerializationException("bad")))
        assertFalse(isNetworkFailure(IllegalStateException("bug")))
    }
}
