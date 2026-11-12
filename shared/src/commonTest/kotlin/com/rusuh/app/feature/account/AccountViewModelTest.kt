package com.rusuh.app.feature.account

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
        val viewModel =
            AccountViewModel(
                getProfile = GetProfileUseCase(repository, names),
                signOut = SignOutUseCase(repository, generation, names),
                observeSession = ObserveSessionUseCase(observer),
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
            assertEquals(AccountBody.SignedOut(sessionExpired = false), f.state.toBody())
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
            assertEquals(AccountBody.ProfileLoading, f.state.toBody())

            send(f, AccountIntent.LoadProfile)

            val body = assertIs<AccountBody.Profile>(f.state.toBody())
            assertEquals(AccountProfileUi("Sari Wulandari", TEST_EMAIL, emailVerified = true), body.profile)
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
            assertEquals(AccountBody.ProfileError, f.state.toBody())

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
            assertEquals(AccountBody.SignedOut(sessionExpired = false), f.state.toBody())
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
            assertEquals(AccountBody.SignedOut(sessionExpired = false), f.state.toBody())
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

            assertEquals(AccountBody.SignedOut(sessionExpired = true), f.state.toBody())

            // Signing in again clears the message.
            f.observer.current.value = SessionPhase.SignedIn
            runCurrent()
            assertFalse(f.state.sessionExpiredNotice)
            f.viewModel.clear()
        }

    // --- Pure state mapping ---

    private fun summary(name: String? = "Sari Wulandari") = AccountSummary("usr_1", TEST_EMAIL, name)

    @Test
    fun theBodyMapsEverySessionAndProfileCombination() {
        val loaded = ProfileLoad.Loaded(summary())
        assertEquals(AccountBody.Restoring, AccountState(phase = SessionPhase.Restoring).toBody())
        assertEquals(AccountBody.SignedOut(false), AccountState(phase = SessionPhase.SignedOut).toBody())
        assertEquals(
            AccountBody.SignedOut(true),
            AccountState(phase = SessionPhase.SignedOut, sessionExpiredNotice = true).toBody(),
        )
        assertEquals(
            AccountBody.ProfileLoading,
            AccountState(phase = SessionPhase.SignedIn, profile = ProfileLoad.Idle).toBody(),
        )
        assertEquals(
            AccountBody.ProfileLoading,
            AccountState(phase = SessionPhase.SignedIn, profile = ProfileLoad.Loading).toBody(),
        )
        assertEquals(
            AccountBody.Profile(AccountProfileUi("Sari Wulandari", TEST_EMAIL, true)),
            AccountState(phase = SessionPhase.SignedIn, profile = loaded).toBody(),
        )
        assertEquals(
            AccountBody.ProfileError,
            AccountState(phase = SessionPhase.SignedIn, profile = ProfileLoad.Failed).toBody(),
        )
    }

    @Test
    fun theNameStepKeepsTheLoginOnScreenWhileTheSessionExists() {
        val state = AccountState(phase = SessionPhase.SignedIn, profile = ProfileLoad.Loaded(summary()))
        assertEquals(AccountBody.SignedOut(false), state.toBody(signInHoldsAccountPage = true))
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
