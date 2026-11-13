package com.rusuh.app.feature.account

import com.rusuh.app.analytics.Analytics
import com.rusuh.app.analytics.AnalyticsConsent
import com.rusuh.app.analytics.AnalyticsConsentStore
import com.rusuh.app.data.storage.FakeAppPreferences
import com.rusuh.app.domain.model.AccountSummary
import com.rusuh.app.domain.model.AuthUser
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.session.SessionPhase
import com.rusuh.app.domain.usecase.AuthGeneration
import com.rusuh.app.domain.usecase.GetProfileUseCase
import com.rusuh.app.domain.usecase.ObserveSessionUseCase
import com.rusuh.app.domain.usecase.SignOutUseCase
import com.rusuh.app.feature.auth.FakeAuthRepository
import com.rusuh.app.feature.auth.FakeDisplayNames
import com.rusuh.app.feature.auth.FakeSessionObserver
import com.rusuh.app.feature.auth.TEST_EMAIL
import com.rusuh.app.feature.auth.TEST_USER
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(scope: TestScope, phase: SessionPhase = SessionPhase.Restoring) {
        val repository = FakeAuthRepository()
        val names = FakeDisplayNames()
        val observer = FakeSessionObserver(phase)
        val generation = AuthGeneration()
        val prefs = FakeAppPreferences()
        val consentStore = AnalyticsConsentStore(prefs)
        val analytics = FakeAnalytics()
        val viewModel =
            AccountViewModel(
                getProfile = GetProfileUseCase(repository, names),
                signOut = SignOutUseCase(repository, generation, names),
                observeSession = ObserveSessionUseCase(observer),
                consentStore = consentStore,
                analytics = analytics,
            )

        init {
            scope.runCurrent()
            // Like the real repository: the local session is gone as soon as the logout starts.
            repository.onLogout = {
                observer.current.value = SessionPhase.SignedOut
                DomainResult.Success(Unit)
            }
        }

        val state get() = viewModel.state.value
    }

    private fun TestScope.fixture(phase: SessionPhase = SessionPhase.Restoring) = Fixture(this, phase)

    private fun TestScope.send(
        f: Fixture,
        intent: AccountIntent,
    ) {
        f.viewModel.dispatch(intent)
        runCurrent()
    }

    @Test
    fun startsRestoringAndNeverShowsTheLoginBeforeTheSessionIsRead() =
        runTest(dispatcher) {
            val f = fixture()
            assertEquals(AccountBody.Restoring, f.state.toBody())
            f.viewModel.clear()
        }

    @Test
    fun aRestoredSessionThatIsSignedOutShowsTheLogin() =
        runTest(dispatcher) {
            val f = fixture()
            f.observer.current.value = SessionPhase.SignedOut
            runCurrent()
            assertEquals(
                AccountBody.SignedOut(sessionExpired = false, analyticsEnabled = false),
                f.state.toBody(),
            )
            f.viewModel.clear()
        }

    @Test
    fun aHangingRestoreFallsBackToTheLoginAfterTheBoundedWait() =
        runTest(dispatcher) {
            val f = fixture()
            assertEquals(1, f.observer.awaitCalls)
            assertEquals(SessionPhase.Restoring, f.state.phase)

            // awaitSignedIn gave up (its bound) while the phase is still Restoring.
            f.observer.awaitGate.complete(false)
            runCurrent()
            assertEquals(SessionPhase.SignedOut, f.state.phase)
            f.viewModel.clear()
        }

    @Test
    fun aSignedInSessionLoadsTheProfileFromMe() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onMe = { DomainResult.Success(TEST_USER.copy(fullName = "Sari Wulandari")) }
            f.observer.current.value = SessionPhase.SignedIn
            runCurrent()
            assertEquals(AccountBody.ProfileLoading(analyticsEnabled = false), f.state.toBody())

            send(f, AccountIntent.LoadProfile)

            val body = assertIs<AccountBody.Profile>(f.state.toBody())
            assertEquals(
                AccountProfileUi("Sari Wulandari", TEST_EMAIL, emailVerified = true, analyticsEnabled = false),
                body.profile,
            )
            f.viewModel.clear()
        }

    @Test
    fun releasingTheNameStepResetsAnEarlyProfileLoad() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            send(f, AccountIntent.LoadProfile)
            assertIs<AccountBody.Profile>(f.state.toBody())
            send(f, AccountIntent.NameStepVisibilityChanged(true))
            send(f, AccountIntent.NameStepVisibilityChanged(false))
            assertEquals(ProfileLoad.Idle, f.state.profile)
            f.viewModel.clear()
        }

    @Test
    fun aProfileAnswerStartedBeforeTheNameStepIsDroppedAfterItCloses() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            val gate = CompletableDeferred<Unit>()
            f.repository.onMe = {
                gate.await()
                DomainResult.Success(TEST_USER)
            }
            send(f, AccountIntent.LoadProfile)
            send(f, AccountIntent.NameStepVisibilityChanged(true))
            send(f, AccountIntent.NameStepVisibilityChanged(false))
            gate.complete(Unit)
            runCurrent()
            assertEquals(ProfileLoad.Idle, f.state.profile)
            f.viewModel.clear()
        }

    @Test
    fun anAlreadyRestoredSessionSeedsTheInitialPhase() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            assertEquals(SessionPhase.SignedIn, f.state.phase)
            f.viewModel.clear()
        }

    @Test
    fun loadingTwiceCallsMeOnce() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            val gate = CompletableDeferred<Unit>()
            f.repository.onMe = {
                gate.await()
                DomainResult.Success(TEST_USER)
            }
            send(f, AccountIntent.LoadProfile)
            send(f, AccountIntent.LoadProfile)
            gate.complete(Unit)
            runCurrent()
            send(f, AccountIntent.LoadProfile)

            assertEquals(1, f.repository.meCalls)
            f.viewModel.clear()
        }

    @Test
    fun theDeviceOnlyNameIsUsedWhenMeReturnsNone() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            f.names.set(TEST_USER.userId, "Sari Wulandari")
            send(f, AccountIntent.LoadProfile)

            val body = assertIs<AccountBody.Profile>(f.state.toBody())
            assertEquals("Sari Wulandari", body.profile.name)
            f.viewModel.clear()
        }

    @Test
    fun aDeviceNameOfAnotherAccountIsNotUsed() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            f.names.set("usr_someone_else", "Someone Else")
            send(f, AccountIntent.LoadProfile)

            val body = assertIs<AccountBody.Profile>(f.state.toBody())
            assertEquals("test.user", body.profile.name)
            f.viewModel.clear()
        }

    @Test
    fun aFailedLoadShowsTheRetryStateAndRetryRecovers() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            f.repository.onMe = { DomainResult.NetworkError }
            send(f, AccountIntent.LoadProfile)
            assertEquals(AccountBody.ProfileError(analyticsEnabled = false), f.state.toBody())

            f.repository.onMe = { DomainResult.Success(TEST_USER) }
            send(f, AccountIntent.RetryProfile)
            assertIs<AccountBody.Profile>(f.state.toBody())
            f.viewModel.clear()
        }

    @Test
    fun anAnswerThatArrivesAfterASignOutIsDropped() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            val gate = CompletableDeferred<Unit>()
            f.repository.onMe = {
                gate.await()
                DomainResult.Success(TEST_USER)
            }
            send(f, AccountIntent.LoadProfile)
            f.observer.current.value = SessionPhase.SignedOut
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertEquals(ProfileLoad.Idle, f.state.profile)
            assertEquals(
                AccountBody.SignedOut(sessionExpired = false, analyticsEnabled = false),
                f.state.toBody(),
            )
            f.viewModel.clear()
        }

    @Test
    fun signOutClearsTheSessionAndTheLocalNameAndShowsTheLogin() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            f.names.set(TEST_USER.userId, "Sari Wulandari")
            send(f, AccountIntent.LoadProfile)

            send(f, AccountIntent.SignOut)

            assertEquals(1, f.repository.logouts)
            assertEquals(1, f.names.clears)
            assertNull(f.names.name)
            assertEquals(
                AccountBody.SignedOut(sessionExpired = false, analyticsEnabled = false),
                f.state.toBody(),
            )
            assertFalse(f.state.isSigningOut)
            f.viewModel.clear()
        }

    @Test
    fun aDoubleTapOnSignOutSignsOutOnce() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            val gate = CompletableDeferred<Unit>()
            f.repository.onLogout = {
                gate.await()
                DomainResult.Success(Unit)
            }
            f.viewModel.dispatch(AccountIntent.SignOut)
            f.viewModel.dispatch(AccountIntent.SignOut)
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertEquals(1, f.repository.logouts)
            f.viewModel.clear()
        }

    @Test
    fun signOutMovesTheGenerationOnSoALateSignInIsDropped() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            val before = f.generation.current()
            send(f, AccountIntent.SignOut)
            assertTrue(f.generation.current() > before)
            f.viewModel.clear()
        }

    @Test
    fun anExpiredSessionReturnsToTheLoginWithAShortMessage() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            send(f, AccountIntent.LoadProfile)
            assertIs<AccountBody.Profile>(f.state.toBody())

            f.observer.expiredEvents.emit(Unit)
            f.observer.current.value = SessionPhase.SignedOut
            runCurrent()

            assertEquals(
                AccountBody.SignedOut(sessionExpired = true, analyticsEnabled = false),
                f.state.toBody(),
            )

            // Signing in again clears the message.
            f.observer.current.value = SessionPhase.SignedIn
            runCurrent()
            assertFalse(f.state.sessionExpiredNotice)
            f.viewModel.clear()
        }

    @Test
    fun signOutClearsUserId() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            f.analytics.setTestUserId("usr_123")
            send(f, AccountIntent.SignOut)
            assertNull(f.analytics.recordedUserId)
            f.viewModel.clear()
        }

    @Test
    fun setAnalyticsConsentUpdatesStoreAndEnablesCollection() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedOut)
            send(f, AccountIntent.SetAnalyticsConsent(true))
            assertEquals(AnalyticsConsent.Granted, f.consentStore.consent.value)
            assertEquals(true, f.analytics.collectionEnabled)
            assertEquals(true, assertIs<AccountBody.SignedOut>(f.state.toBody()).analyticsEnabled)
            f.viewModel.clear()
        }

    @Test
    fun setAnalyticsConsentUpdatesStoreAndDisablesCollection() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            send(f, AccountIntent.SetAnalyticsConsent(false))
            assertEquals(AnalyticsConsent.Denied, f.consentStore.consent.value)
            assertEquals(false, f.analytics.collectionEnabled)
            f.viewModel.clear()
        }

    @Test
    fun signedOutBodyReflectsStoredAnalyticsConsent() {
        assertEquals(
            AccountBody.SignedOut(sessionExpired = false, analyticsEnabled = true),
            AccountState(phase = SessionPhase.SignedOut, analyticsConsent = AnalyticsConsent.Granted).toBody(),
        )
    }

    // --- Pure state mapping ---

    private fun summary(name: String? = "Sari Wulandari") = AccountSummary("usr_1", TEST_EMAIL, name)

    @Test
    fun theBodyMapsEverySessionAndProfileCombination() {
        val loaded = ProfileLoad.Loaded(summary())
        assertEquals(AccountBody.Restoring, AccountState(phase = SessionPhase.Restoring).toBody())
        assertEquals(
            AccountBody.SignedOut(sessionExpired = false, analyticsEnabled = false),
            AccountState(phase = SessionPhase.SignedOut).toBody(),
        )
        assertEquals(
            AccountBody.SignedOut(sessionExpired = true, analyticsEnabled = false),
            AccountState(phase = SessionPhase.SignedOut, sessionExpiredNotice = true).toBody(),
        )
        assertEquals(
            AccountBody.ProfileLoading(analyticsEnabled = false),
            AccountState(phase = SessionPhase.SignedIn, profile = ProfileLoad.Idle).toBody(),
        )
        assertEquals(
            AccountBody.ProfileLoading(analyticsEnabled = false),
            AccountState(phase = SessionPhase.SignedIn, profile = ProfileLoad.Loading).toBody(),
        )
        assertEquals(
            AccountBody.Profile(AccountProfileUi("Sari Wulandari", TEST_EMAIL, true, analyticsEnabled = false)),
            AccountState(phase = SessionPhase.SignedIn, profile = loaded).toBody(),
        )
        assertEquals(
            AccountBody.ProfileError(analyticsEnabled = false),
            AccountState(phase = SessionPhase.SignedIn, profile = ProfileLoad.Failed).toBody(),
        )
    }

    @Test
    fun theNameStepKeepsTheLoginOnScreenWhileTheSessionExists() {
        val state = AccountState(phase = SessionPhase.SignedIn, profile = ProfileLoad.Loaded(summary()))
        assertEquals(
            AccountBody.SignedOut(sessionExpired = false, analyticsEnabled = false),
            state.toBody(signInHoldsAccountPage = true),
        )
    }

    @Test
    fun theProfileNameFallsBackToTheEmailBeforeTheAt() {
        assertEquals("test.user", summary(name = null).toUi().name)
        assertEquals("test.user", summary(name = "  ").toUi().name)
        assertEquals("Sari Wulandari", summary().toUi().name)
        assertTrue(summary().toUi().emailVerified)
    }

    @Test
    fun meWithABlankNameCountsAsNoName() =
        runTest(dispatcher) {
            val f = fixture(SessionPhase.SignedIn)
            f.repository.onMe = {
                DomainResult.Success(AuthUser("usr_1", TEST_EMAIL, fullName = " ", locale = null))
            }
            send(f, AccountIntent.LoadProfile)

            val loaded = assertIs<ProfileLoad.Loaded>(f.state.profile)
            assertNull(loaded.profile.name)
            f.viewModel.clear()
        }
}

private class FakeAnalytics : Analytics {
    private var userIdValue: String? = null
    val recordedUserId: String? get() = userIdValue
    var collectionEnabled: Boolean? = null

    fun setTestUserId(id: String?) {
        userIdValue = id
    }

    override fun log(
        event: String,
        params: Map<String, Any>,
    ) = Unit

    override fun setUserId(id: String?) {
        userIdValue = id
    }

    override fun setUserProperty(
        name: String,
        value: String?,
    ) = Unit

    override fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
    }
}
