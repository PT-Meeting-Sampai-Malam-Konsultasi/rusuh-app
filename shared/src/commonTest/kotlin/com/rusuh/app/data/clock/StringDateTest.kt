package com.rusuh.app.data.clock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StringDateTest {
    private val base = 1_794_652_800_000L

    @Test
    fun parsesZuluTime() = assertEquals(base, parseStringDateMillis("2026-11-14T10:40:00Z"))

    @Test
    fun keepsMillisecondsAndDropsFinerDigits() {
        assertEquals(base + 123, parseStringDateMillis("2026-11-14T10:40:00.123Z"))
        assertEquals(base + 123, parseStringDateMillis("2026-11-14T10:40:00.123456789Z"))
    }

    @Test
    fun appliesTheOffset() = assertEquals(base, parseStringDateMillis("2026-11-14T17:40:00+07:00"))

    @Test
    fun ignoresSurroundingWhitespace() = assertEquals(base, parseStringDateMillis(" 2026-11-14T10:40:00Z "))

    @Test
    fun rejectsMalformedText() {
        assertNull(parseStringDateMillis("garbage"))
        assertNull(parseStringDateMillis(""))
        assertNull(parseStringDateMillis("2026-11-14T10:40:00"))
        assertNull(parseStringDateMillis("2026-13-40T10:40:00Z"))
    }
}
