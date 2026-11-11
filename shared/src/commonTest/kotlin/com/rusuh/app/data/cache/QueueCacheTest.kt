package com.rusuh.app.data.cache

import com.rusuh.app.data.storage.FakeAppPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class QueueCacheTest {
    @Test
    fun remembersJoinedEventsInOrderWithoutDuplicates() =
        runTest {
            val cache = QueueCache(FakeAppPreferences())

            cache.addJoinedEvent("a")
            cache.addJoinedEvent("b")
            cache.addJoinedEvent("a")

            assertEquals(listOf("a", "b"), cache.getJoinedEventIds())
        }

    @Test
    fun removesOneEventAndToleratesAnUnknownOne() =
        runTest {
            val cache = QueueCache(FakeAppPreferences())
            cache.addJoinedEvent("a")
            cache.addJoinedEvent("b")

            cache.removeJoinedEvent("a")
            cache.removeJoinedEvent("zzz")

            assertEquals(listOf("b"), cache.getJoinedEventIds())
        }

    @Test
    fun storesAJsonListUnderTheQueuePrefix() =
        runTest {
            val prefs = FakeAppPreferences()
            QueueCache(prefs).addJoinedEvent("evt_1")

            assertEquals(mapOf("queue_joined_events_json" to """["evt_1"]"""), prefs.entries)
        }

    @Test
    fun aCorruptStoredValueReadsAsEmptyAndIsReplacedOnTheNextWrite() =
        runTest {
            val prefs = FakeAppPreferences()
            prefs.entries["queue_joined_events_json"] = "{not a list"
            val cache = QueueCache(prefs)

            assertEquals(emptyList(), cache.getJoinedEventIds())
            cache.addJoinedEvent("a")

            assertEquals(listOf("a"), cache.getJoinedEventIds())
        }

    @Test
    fun theAdmissionDetailsRoundTripWithoutAnyToken() =
        runTest {
            val prefs = FakeAppPreferences()
            val cache = QueueCache(prefs)
            val info = AdmissionInfo("evt_1", "s_01", 3, 123L)
            assertEquals(null, cache.getAdmission())

            cache.setAdmission(info)

            assertEquals(info, cache.getAdmission())
            assertEquals(listOf("queue_admission_json"), prefs.entries.keys.toList())
            cache.clearAdmission()
            assertEquals(null, cache.getAdmission())
        }

    @Test
    fun aCorruptAdmissionReadsAsNone() =
        runTest {
            val prefs = FakeAppPreferences()
            prefs.entries["queue_admission_json"] = "oops"

            assertEquals(null, QueueCache(prefs).getAdmission())
        }

    @Test
    fun theDeviceCacheKeepsTheTokenExpiry() =
        runTest {
            val cache = DeviceCache(FakeAppPreferences())
            assertEquals(null, cache.getTokenExpiresAt())

            cache.setTokenExpiresAt(123L)
            assertEquals(123L, cache.getTokenExpiresAt())

            cache.clearTokenExpiresAt()
            assertEquals(null, cache.getTokenExpiresAt())
        }
}
