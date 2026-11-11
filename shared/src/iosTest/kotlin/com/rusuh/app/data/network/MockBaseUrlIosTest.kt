package com.rusuh.app.data.network

import kotlin.test.Test
import kotlin.test.assertEquals

class MockBaseUrlIosTest {
    @Test
    fun simulatorUsesLocalhost() {
        assertEquals("http://localhost:3000", platformMockBaseUrl)
    }
}
