package com.rusuh.app.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnalyticsEventsTest {
    @Test
    fun positionBucketsCoverDefinedRanges() {
        assertNull(AnalyticsEvents.positionBucket(0))
        assertEquals("1-100", AnalyticsEvents.positionBucket(1))
        assertEquals("1-100", AnalyticsEvents.positionBucket(100))
        assertEquals("101-1000", AnalyticsEvents.positionBucket(101))
        assertEquals("101-1000", AnalyticsEvents.positionBucket(1_000))
        assertEquals("1001-10000", AnalyticsEvents.positionBucket(1_001))
        assertEquals("1001-10000", AnalyticsEvents.positionBucket(10_000))
        assertEquals("10000+", AnalyticsEvents.positionBucket(10_001))
    }

    @Test
    fun waitBucketsCoverDefinedRanges() {
        assertNull(AnalyticsEvents.waitBucket(-1))
        assertEquals("0-60", AnalyticsEvents.waitBucket(0))
        assertEquals("0-60", AnalyticsEvents.waitBucket(60))
        assertEquals("61-300", AnalyticsEvents.waitBucket(61))
        assertEquals("61-300", AnalyticsEvents.waitBucket(300))
        assertEquals("301-900", AnalyticsEvents.waitBucket(301))
        assertEquals("301-900", AnalyticsEvents.waitBucket(900))
        assertEquals("900+", AnalyticsEvents.waitBucket(901))
    }
}
