package com.rusuh.app.data.network

import kotlin.test.Test
import kotlin.test.assertEquals

class MockBaseUrlAndroidTest {
    @Test
    fun emulatorReachesTheHostThrough10002() {
        assertEquals("http://10.0.2.2:3000", platformMockBaseUrl)
    }
}
