package com.rusuh.app.data.cache

import com.rusuh.app.data.storage.AppPreferences
import com.rusuh.app.data.storage.FakeAppPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PrefsCacheTest {
    private val prefs = FakeAppPreferences()

    @Test
    fun homeCacheStoresUnderItsPrefix() =
        runTest {
            val cache = HomeCache(prefs)
            assertNull(cache.getEvents())

            cache.setEvents("[]")

            assertEquals("[]", cache.getEvents())
            assertEquals(listOf("home_events_json"), prefs.entries.keys.toList())

            cache.clearEvents()
            assertNull(cache.getEvents())
        }

    @Test
    fun keysOutsideThePrefixAreRejected() =
        runTest {
            val cache = TestCache(prefs)
            assertFailsWith<IllegalArgumentException> { cache.put("queue_x", "1") }
            assertEquals(0, prefs.entries.size)
        }

    @Test
    fun booleansAreStoredAsStrings() =
        runTest {
            val cache = TestCache(prefs)
            cache.putBoolean("tix_seen", true)
            assertEquals("true", prefs.entries["tix_seen"])
            assertEquals(true, cache.getBoolean("tix_seen"))

            prefs.entries["tix_seen"] = "garbage"
            assertNull(cache.getBoolean("tix_seen"))
        }

    private class TestCache(
        prefs: AppPreferences,
    ) : PrefsCache(prefs, "tix_") {
        suspend fun put(
            key: String,
            value: String,
        ) = write(key, value)

        suspend fun putBoolean(
            key: String,
            value: Boolean,
        ) = writeBoolean(key, value)

        suspend fun getBoolean(key: String): Boolean? = readBoolean(key)
    }
}
