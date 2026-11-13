package com.rusuh.app.crash

import com.rusuh.app.analytics.AnalyticsConsent
import com.rusuh.app.analytics.AnalyticsConsentStore
import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.data.storage.FakeAppPreferences
import com.rusuh.app.di.BackendMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashReporterTest {
    private fun fixture(
        environment: AppEnvironment = AppEnvironment.Staging,
        backendMode: BackendMode = BackendMode.Real,
    ) = Fixture(environment, backendMode)

    private class Fixture(
        environment: AppEnvironment,
        backendMode: BackendMode,
    ) {
        val prefs = FakeAppPreferences()
        val consentStore = AnalyticsConsentStore(prefs)
        val delegate = FakeCrashReporter()
        val reporter = ConsentAwareCrashReporter(delegate, consentStore, environment, backendMode)
    }

    @Test
    fun `recordNonFatal does nothing when consent is Unknown`() =
        runTest {
            val f = fixture()
            f.reporter.recordNonFatal(RuntimeException("test"))
            assertTrue(f.delegate.nonFatals.isEmpty())
        }

    @Test
    fun `recordNonFatal does nothing when consent is Denied`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Denied)
            f.reporter.recordNonFatal(RuntimeException("test"))
            assertTrue(f.delegate.nonFatals.isEmpty())
        }

    @Test
    fun `recordNonFatal forwards when consent is Granted`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Granted)
            f.reporter.recordNonFatal(RuntimeException("test"))
            assertEquals(1, f.delegate.nonFatals.size)
        }

    @Test
    fun `recordNonFatal does nothing in Mock environment`() =
        runTest {
            val f = fixture(environment = AppEnvironment.Mock)
            f.consentStore.set(AnalyticsConsent.Granted)
            f.reporter.recordNonFatal(RuntimeException("test"))
            assertTrue(f.delegate.nonFatals.isEmpty())
        }

    @Test
    fun `recordNonFatal does nothing in Stub mode`() =
        runTest {
            val f = fixture(backendMode = BackendMode.Stub)
            f.consentStore.set(AnalyticsConsent.Granted)
            f.reporter.recordNonFatal(RuntimeException("test"))
            assertTrue(f.delegate.nonFatals.isEmpty())
        }

    @Test
    fun `setCustomKey forwards when consent is Granted`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Granted)
            f.reporter.setCustomKey("key", "value")
            assertEquals("value", f.delegate.customKeys["key"])
        }

    @Test
    fun `setCustomKey does nothing when consent is Denied`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Denied)
            f.reporter.setCustomKey("key", "value")
            assertTrue(f.delegate.customKeys.isEmpty())
        }

    @Test
    fun `log scrubs messages and forwards when consent is Granted`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Granted)
            f.reporter.log("user@example.com logged in")
            assertEquals(1, f.delegate.logs.size)
            assertTrue(f.delegate.logs[0].contains("[redacted-email]"))
        }

    @Test
    fun `log does nothing when consent is Denied`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Denied)
            f.reporter.log("test message")
            assertTrue(f.delegate.logs.isEmpty())
        }

    @Test
    fun `setUserId forwards when consent is Granted`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Granted)
            f.reporter.setUserId("usr_123")
            assertEquals("usr_123", f.delegate.userId)
        }

    @Test
    fun `setUserId clears when consent is Denied`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Denied)
            f.reporter.setUserId("usr_123")
            assertNull(f.delegate.userId)
        }

    @Test
    fun `setUserId null clears the user id`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Granted)
            f.reporter.setUserId("usr_123")
            f.reporter.setUserId(null)
            assertNull(f.delegate.userId)
        }

    @Test
    fun `setCollectionEnabled forwards in Staging`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Granted)
            f.reporter.setCollectionEnabled(true)
            assertEquals(true, f.delegate.collectionEnabled)
            f.reporter.setCollectionEnabled(false)
            assertEquals(false, f.delegate.collectionEnabled)
        }

    @Test
    fun `setCollectionEnabled cannot enable collection before consent`() =
        runTest {
            val f = fixture()
            f.reporter.setCollectionEnabled(true)
            assertEquals(false, f.delegate.collectionEnabled)
        }

    @Test
    fun `setCollectionEnabled does nothing in Mock environment`() =
        runTest {
            val f = fixture(environment = AppEnvironment.Mock)
            f.reporter.setCollectionEnabled(true)
            assertNull(f.delegate.collectionEnabled)
        }

    @Test
    fun `setCollectionEnabled does nothing in Stub mode`() =
        runTest {
            val f = fixture(backendMode = BackendMode.Stub)
            f.reporter.setCollectionEnabled(true)
            assertNull(f.delegate.collectionEnabled)
        }
}
