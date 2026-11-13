package com.rusuh.app.analytics

import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.data.storage.FakeAppPreferences
import com.rusuh.app.di.BackendMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConsentAwareAnalyticsTest {
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
        val delegate = RecordingAnalytics()
        val analytics = ConsentAwareAnalytics(delegate, consentStore, environment, backendMode)
    }

    @Test
    fun `log does nothing when consent is Unknown`() =
        runTest {
            val f = fixture()
            f.analytics.log("test_event", mapOf("key" to "value"))
            assertEquals(0, f.delegate.events.size)
        }

    @Test
    fun `log does nothing when consent is Denied`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Denied)
            f.analytics.log("test_event", mapOf("key" to "value"))
            assertEquals(0, f.delegate.events.size)
        }

    @Test
    fun `log forwards when consent is Granted`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Granted)
            f.analytics.log("test_event", mapOf("key" to "value"))
            assertEquals(1, f.delegate.events.size)
            assertEquals("test_event", f.delegate.events[0].first)
        }

    @Test
    fun `log does nothing in Mock environment`() =
        runTest {
            val f = fixture(environment = AppEnvironment.Mock)
            f.consentStore.set(AnalyticsConsent.Granted)
            f.analytics.log("test_event")
            assertEquals(0, f.delegate.events.size)
        }

    @Test
    fun `log does nothing in Stub mode`() =
        runTest {
            val f = fixture(backendMode = BackendMode.Stub)
            f.consentStore.set(AnalyticsConsent.Granted)
            f.analytics.log("test_event")
            assertEquals(0, f.delegate.events.size)
        }

    @Test
    fun `setUserId forwards when consent is Granted`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Granted)
            f.analytics.setUserId("usr_123")
            assertEquals("usr_123", f.delegate.recordedUserId)
        }

    @Test
    fun `setUserId clears when consent is Denied`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Denied)
            f.analytics.setUserId("usr_123")
            assertNull(f.delegate.recordedUserId)
        }

    @Test
    fun `setUserId null clears the user id`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Granted)
            f.analytics.setUserId("usr_123")
            f.analytics.setUserId(null)
            assertNull(f.delegate.recordedUserId)
        }

    @Test
    fun `setCollectionEnabled forwards in Staging`() =
        runTest {
            val f = fixture()
            f.consentStore.set(AnalyticsConsent.Granted)
            f.analytics.setCollectionEnabled(true)
            assertEquals(true, f.delegate.collectionEnabled)
            f.analytics.setCollectionEnabled(false)
            assertEquals(false, f.delegate.collectionEnabled)
        }

    @Test
    fun `setCollectionEnabled cannot enable collection before consent`() =
        runTest {
            val f = fixture()
            f.analytics.setCollectionEnabled(true)
            assertEquals(false, f.delegate.collectionEnabled)
        }

    @Test
    fun `setCollectionEnabled does nothing in Mock environment`() =
        runTest {
            val f = fixture(environment = AppEnvironment.Mock)
            f.analytics.setCollectionEnabled(true)
            assertNull(f.delegate.collectionEnabled)
        }

    @Test
    fun `setCollectionEnabled does nothing in Stub mode`() =
        runTest {
            val f = fixture(backendMode = BackendMode.Stub)
            f.analytics.setCollectionEnabled(true)
            assertNull(f.delegate.collectionEnabled)
        }
}

private class RecordingAnalytics : Analytics {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private var userIdValue: String? = null
    val recordedUserId: String? get() = userIdValue
    val userProperties = mutableMapOf<String, String?>()
    var collectionEnabled: Boolean? = null

    override fun log(
        event: String,
        params: Map<String, Any>,
    ) {
        events.add(event to params)
    }

    override fun setUserId(id: String?) {
        userIdValue = id
    }

    override fun setUserProperty(
        name: String,
        value: String?,
    ) {
        userProperties[name] = value
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
    }
}
