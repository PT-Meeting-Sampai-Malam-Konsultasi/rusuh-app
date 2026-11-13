package com.rusuh.app.feature.consent

import com.rusuh.app.analytics.Analytics
import com.rusuh.app.analytics.AnalyticsConsent
import com.rusuh.app.analytics.AnalyticsConsentStore
import com.rusuh.app.data.storage.FakeAppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConsentViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(scope: TestScope) {
        val prefs = FakeAppPreferences()
        val consentStore = AnalyticsConsentStore(prefs)
        val analytics = FakeAnalytics()
        val viewModel = ConsentViewModel(consentStore, analytics)
        val effects = mutableListOf<ConsentEffect>()

        init {
            scope.backgroundScope.launch { viewModel.effect.collect { effects += it } }
            scope.runCurrent()
        }
    }

    private fun TestScope.fixture() = Fixture(this)

    @Test
    fun `Accept sets consent to Granted and emits Done`() =
        runTest {
            val f = fixture()
            f.viewModel.dispatch(ConsentIntent.Accept)
            runCurrent()
            assertEquals(AnalyticsConsent.Granted, f.consentStore.consent.value)
            assertEquals(1, f.effects.size)
            assertIs<ConsentEffect.Done>(f.effects[0])
        }

    @Test
    fun `Accept enables collection`() =
        runTest {
            val f = fixture()
            f.viewModel.dispatch(ConsentIntent.Accept)
            runCurrent()
            assertTrue(f.analytics.collectionEnabled == true)
        }

    @Test
    fun `Decline sets consent to Denied and emits Done`() =
        runTest {
            val f = fixture()
            f.viewModel.dispatch(ConsentIntent.Decline)
            runCurrent()
            assertEquals(AnalyticsConsent.Denied, f.consentStore.consent.value)
            assertEquals(1, f.effects.size)
            assertIs<ConsentEffect.Done>(f.effects[0])
        }

    @Test
    fun `Decline disables collection`() =
        runTest {
            val f = fixture()
            f.viewModel.dispatch(ConsentIntent.Decline)
            runCurrent()
            assertTrue(f.analytics.collectionEnabled == false)
        }
}

private class FakeAnalytics : Analytics {
    var collectionEnabled: Boolean? = null

    override fun log(
        event: String,
        params: Map<String, Any>,
    ) = Unit

    override fun setUserId(id: String?) = Unit

    override fun setUserProperty(
        name: String,
        value: String?,
    ) = Unit

    override fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
    }
}
