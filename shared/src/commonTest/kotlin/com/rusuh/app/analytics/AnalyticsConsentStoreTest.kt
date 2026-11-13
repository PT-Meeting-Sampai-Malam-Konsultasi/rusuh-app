package com.rusuh.app.analytics

import com.rusuh.app.data.storage.FakeAppPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsConsentStoreTest {
    private fun fixture() = Fixture()

    private class Fixture {
        val prefs = FakeAppPreferences()
        val store = AnalyticsConsentStore(prefs)
    }

    @Test
    fun `initial state is Unknown`() =
        runTest {
            val f = fixture()
            assertEquals(AnalyticsConsent.Unknown, f.store.consent.value)
        }

    @Test
    fun `load reads Unknown when no value stored`() =
        runTest {
            val f = fixture()
            f.store.load()
            assertEquals(AnalyticsConsent.Unknown, f.store.consent.value)
        }

    @Test
    fun `load reads persisted Granted`() =
        runTest {
            val f = fixture()
            f.prefs.entries["analytics_consent"] = "Granted"
            f.store.load()
            assertEquals(AnalyticsConsent.Granted, f.store.consent.value)
        }

    @Test
    fun `load reads persisted Denied`() =
        runTest {
            val f = fixture()
            f.prefs.entries["analytics_consent"] = "Denied"
            f.store.load()
            assertEquals(AnalyticsConsent.Denied, f.store.consent.value)
        }

    @Test
    fun `set persists and updates flow`() =
        runTest {
            val f = fixture()
            f.store.set(AnalyticsConsent.Granted)
            assertEquals(AnalyticsConsent.Granted, f.store.consent.value)
            assertEquals("Granted", f.prefs.entries["analytics_consent"])
        }

    @Test
    fun `set Denied persists and updates flow`() =
        runTest {
            val f = fixture()
            f.store.set(AnalyticsConsent.Denied)
            assertEquals(AnalyticsConsent.Denied, f.store.consent.value)
            assertEquals("Denied", f.prefs.entries["analytics_consent"])
        }
}
