package com.rusuh.app.feature.auth

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import com.rusuh.app.domain.model.UserLocale
import com.rusuh.app.domain.session.SessionPhase
import com.rusuh.app.domain.usecase.AuthGeneration
import com.rusuh.app.domain.usecase.ObserveSessionUseCase
import com.rusuh.app.domain.usecase.RequestOtpUseCase
import com.rusuh.app.domain.usecase.SaveDisplayNameUseCase
import com.rusuh.app.domain.usecase.VerifyOtpUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
class SignInViewModelTest {
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
        val repository = FakeAuthRepository()
        val generation = AuthGeneration()
        val names = FakeDisplayNames()
        val observer = FakeSessionObserver()
        val clock = TestClock(scope.testScheduler)
        val effects = mutableListOf<SignInEffect>()
        val viewModel =
            SignInViewModel(
                requestOtp = RequestOtpUseCase(repository) { UserLocale.Indonesian },
                verifyOtp = VerifyOtpUseCase(repository, generation),
                saveDisplayName = SaveDisplayNameUseCase(names, FakeAuthRepository()),
                observeSession = ObserveSessionUseCase(observer),
                clock = clock,
            )

        init {
            scope.backgroundScope.launch { viewModel.effect.collect { effects += it } }
            scope.runCurrent()
        }

        val state get() = viewModel.state.value
        val step get() = state.step
    }

    private fun TestScope.fixture() = Fixture(this)

    private fun TestScope.send(
        f: Fixture,
        vararg intents: SignInIntent,
    ) {
        intents.forEach(f.viewModel::dispatch)
        runCurrent()
    }

    /** Email entered and the code requested: now on the Code step. */
    private fun TestScope.onCodeStep(f: Fixture) {
        send(f, SignInIntent.EmailChanged(TEST_EMAIL), SignInIntent.SendCode)
        assertIs<SignInUiState.Code>(f.step)
    }

    /** Moves virtual time forward and runs what is due exactly at the end. */
    private fun TestScope.elapse(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun TestScope.finish(f: Fixture) {
        f.viewModel.clear()
        runCurrent()
    }

    // --- Email step and the code request ---

    @Test
    fun startsOnAnEmptyEmailStep() =
        runTest(dispatcher) {
            val f = fixture()
            assertEquals(SignInUiState.Email(""), f.step)
            assertFalse(f.state.isBusy)
            assertNull(f.state.error)
            finish(f)
        }

    @Test
    fun sendCodeTrimsTheEmailAndPassesTheAppLocale() =
        runTest(dispatcher) {
            val f = fixture()
            send(f, SignInIntent.EmailChanged("  $TEST_EMAIL "), SignInIntent.SendCode)

            val request = f.repository.requests.single()
            assertEquals(TEST_EMAIL, request.email)
            assertEquals(UserLocale.Indonesian, request.locale)
            assertNull(request.fullName, "the name is asked after the code, not before")
            val code = assertIs<SignInUiState.Code>(f.step)
            assertEquals(TEST_EMAIL, code.email)
            finish(f)
        }

    @Test
    fun anImplausibleEmailNeverReachesTheServer() =
        runTest(dispatcher) {
            val f = fixture()
            send(f, SignInIntent.EmailChanged("not-an-email"), SignInIntent.SendCode)

            assertTrue(f.repository.requests.isEmpty())
            assertIs<SignInUiState.Email>(f.step)
            finish(f)
        }

    @Test
    fun aRejectedRequestShowsTheInvalidInputLineAndCanBeRetried() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onRequest = { problem(400, ErrorCode.UNKNOWN) }
            send(f, SignInIntent.EmailChanged(TEST_EMAIL), SignInIntent.SendCode)

            assertEquals(SignInError.InvalidInput, f.state.error)
            assertFalse(f.state.isBusy)
            assertIs<SignInUiState.Email>(f.step)

            f.repository.onRequest = { DomainResult.Success(challenge()) }
            send(f, SignInIntent.SendCode)
            assertIs<SignInUiState.Code>(f.step)
            assertNull(f.state.error)
            finish(f)
        }

    @Test
    fun editingTheEmailClearsAnOldError() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onRequest = { DomainResult.NetworkError }
            send(f, SignInIntent.EmailChanged(TEST_EMAIL), SignInIntent.SendCode)
            assertEquals(SignInError.Network, f.state.error)

            send(f, SignInIntent.EmailChanged("other@example.test"))
            assertNull(f.state.error)
            finish(f)
        }

    @Test
    fun aNetworkFailureShowsTheNetworkLineAndRetrySucceeds() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onRequest = { DomainResult.NetworkError }
            send(f, SignInIntent.EmailChanged(TEST_EMAIL), SignInIntent.SendCode)
            assertEquals(SignInError.Network, f.state.error)
            assertIs<SignInUiState.Email>(f.step)

            f.repository.onRequest = { DomainResult.Success(challenge()) }
            send(f, SignInIntent.SendCode)
            assertIs<SignInUiState.Code>(f.step)
            assertEquals(2, f.repository.requests.size)
            finish(f)
        }

    @Test
    fun anUnknownFailureShowsTheGenericLine() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onRequest = { problem(500, ErrorCode.UNKNOWN) }
            send(f, SignInIntent.EmailChanged(TEST_EMAIL), SignInIntent.SendCode)
            assertEquals(SignInError.Unknown, f.state.error)

            f.repository.onRequest = { DomainResult.TechnicalError }
            send(f, SignInIntent.SendCode)
            assertEquals(SignInError.Unknown, f.state.error)
            finish(f)
        }

    @Test
    fun rateLimitingCountsDownFromRetryAfterAndBlocksSending() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onRequest = { problem(429, ErrorCode.RATE_LIMITED, retryAfter = 30) }
            send(f, SignInIntent.EmailChanged(TEST_EMAIL), SignInIntent.SendCode)

            assertEquals(SignInError.RateLimited(30), f.state.error)
            assertTrue(f.state.status.blocksPrimary)

            // Blocked while counting: a tap does not call the server.
            send(f, SignInIntent.SendCode)
            assertEquals(1, f.repository.requests.size)

            elapse(10_000)
            assertEquals(SignInError.RateLimited(20), f.state.error)

            elapse(20_000)
            assertNull(f.state.error)
            assertFalse(f.state.status.blocksPrimary)
            f.repository.onRequest = { DomainResult.Success(challenge()) }
            send(f, SignInIntent.SendCode)
            assertIs<SignInUiState.Code>(f.step)
            finish(f)
        }

    @Test
    fun rateLimitingWithoutRetryAfterShowsALineWithoutACountdown() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onRequest = { problem(429, ErrorCode.RATE_LIMITED) }
            send(f, SignInIntent.EmailChanged(TEST_EMAIL), SignInIntent.SendCode)

            assertEquals(SignInError.RateLimited(null), f.state.error)
            assertFalse(f.state.status.blocksPrimary)
            finish(f)
        }

    @Test
    fun aDoubleTapOnSendCodeMakesOneRequest() =
        runTest(dispatcher) {
            val f = fixture()
            val gate = CompletableDeferred<Unit>()
            f.repository.onRequest = {
                gate.await()
                DomainResult.Success(challenge())
            }
            send(f, SignInIntent.EmailChanged(TEST_EMAIL), SignInIntent.SendCode)
            assertTrue(f.state.isBusy)
            send(f, SignInIntent.SendCode, SignInIntent.SendCode, SignInIntent.EmailChanged("x@example.test"))
            gate.complete(Unit)
            runCurrent()

            assertEquals(1, f.repository.requests.size)
            assertEquals(TEST_EMAIL, assertIs<SignInUiState.Code>(f.step).email)
            finish(f)
        }

    // --- Countdowns ---

    @Test
    fun theCodeStepStartsTheResendCooldownAndTheValidityFromTheServerExpiry() =
        runTest(dispatcher) {
            val f = fixture()
            val expiresAt = TestClock.START_MILLIS + 90_000
            f.repository.onRequest = {
                DomainResult.Success(challenge(expiresAtMillis = expiresAt, resendAfterSeconds = 45))
            }
            onCodeStep(f)

            val code = assertIs<SignInUiState.Code>(f.step)
            assertEquals(45, code.resendInSeconds)
            assertEquals(90, code.validForSeconds)
            finish(f)
        }

    @Test
    fun anUnsyncedClockFallsBackToTheDesignValidity() =
        runTest(dispatcher) {
            val f = fixture()
            f.clock.isSynced = false
            // A server expiry that cannot be compared with this clock must not be used.
            f.repository.onRequest = { DomainResult.Success(challenge(expiresAtMillis = 1_000L)) }
            onCodeStep(f)

            assertEquals(SignInPolicy.CODE_VALID_SECONDS, assertIs<SignInUiState.Code>(f.step).validForSeconds)
            finish(f)
        }

    @Test
    fun theCountdownsTickOncePerSecond() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onRequest = {
                DomainResult.Success(challenge(expiresAtMillis = TestClock.START_MILLIS + 120_000))
            }
            onCodeStep(f)

            elapse(1_000)
            var code = assertIs<SignInUiState.Code>(f.step)
            assertEquals(59, code.resendInSeconds)
            assertEquals(119, code.validForSeconds)

            elapse(59_000)
            code = assertIs<SignInUiState.Code>(f.step)
            assertEquals(0, code.resendInSeconds)
            assertEquals(60, code.validForSeconds)
            finish(f)
        }

    @Test
    fun theValidityRunningOutShowsCodeExpiredWithTheTypedCode() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onRequest = {
                DomainResult.Success(challenge(expiresAtMillis = TestClock.START_MILLIS + 5_000))
            }
            onCodeStep(f)
            send(f, SignInIntent.CodeChanged("1234"))

            elapse(4_000)
            assertIs<SignInUiState.Code>(f.step)
            elapse(1_000)
            assertEquals(SignInUiState.CodeExpired(TEST_EMAIL, "1234"), f.step)
            finish(f)
        }

    @Test
    fun aWrongAnswerAfterExpiryKeepsTheExpiredStep() =
        runTest(dispatcher) {
            val f = fixture()
            val gate = CompletableDeferred<Unit>()
            f.repository.onRequest = {
                DomainResult.Success(challenge(expiresAtMillis = TestClock.START_MILLIS + 2_000))
            }
            f.repository.onVerify = { _, _ ->
                gate.await()
                problem(400, ErrorCode.WRONG_CODE)
            }
            onCodeStep(f)
            send(f, SignInIntent.SubmitCode("123456"))
            elapse(2_000)
            assertIs<SignInUiState.CodeExpired>(f.step)
            gate.complete(Unit)
            runCurrent()
            assertEquals(SignInUiState.CodeExpired(TEST_EMAIL, "123456"), f.step)
            finish(f)
        }

    @Test
    fun resendBeforeTheCooldownEndsIsIgnored() =
        runTest(dispatcher) {
            val f = fixture()
            onCodeStep(f)

            send(f, SignInIntent.ResendCode)
            assertEquals(1, f.repository.requests.size)
            finish(f)
        }

    @Test
    fun resendAfterTheCooldownOpensANewChallenge() =
        runTest(dispatcher) {
            val f = fixture()
            onCodeStep(f)
            send(f, SignInIntent.CodeChanged("12"))
            elapse(60_000)
            assertEquals(0, assertIs<SignInUiState.Code>(f.step).resendInSeconds)

            f.repository.onRequest = { DomainResult.Success(challenge(id = "ch_test_2")) }
            send(f, SignInIntent.ResendCode)

            val code = assertIs<SignInUiState.Code>(f.step)
            assertEquals("", code.code, "a new code starts empty")
            assertEquals(60, code.resendInSeconds)
            assertEquals(2, f.repository.requests.size)
            send(f, SignInIntent.SubmitCode("123456"))
            assertEquals("ch_test_2", f.repository.verifies.single().first)
            finish(f)
        }

    @Test
    fun resendFromTheExpiredStepWorksAndAFailureKeepsTheExpiredStep() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onVerify = { _, _ -> problem(410, ErrorCode.CHALLENGE_EXPIRED) }
            onCodeStep(f)
            send(f, SignInIntent.SubmitCode("123456"))
            assertIs<SignInUiState.CodeExpired>(f.step)

            f.repository.onRequest = { DomainResult.NetworkError }
            send(f, SignInIntent.ResendCode)
            assertIs<SignInUiState.CodeExpired>(f.step)
            assertEquals(SignInError.Network, f.state.error)

            f.repository.onRequest = { DomainResult.Success(challenge(id = "ch_test_3")) }
            send(f, SignInIntent.ResendCode)
            assertIs<SignInUiState.Code>(f.step)
            assertNull(f.state.error)
            finish(f)
        }

    @Test
    fun changeEmailGoesBackToTheEmailStepAndStopsTheCountdown() =
        runTest(dispatcher) {
            val f = fixture()
            onCodeStep(f)

            send(f, SignInIntent.ChangeEmail)
            assertEquals(SignInUiState.Email(TEST_EMAIL), f.step)
            assertNull(f.state.challenge)

            elapse(700_000)
            assertEquals(SignInUiState.Email(TEST_EMAIL), f.step, "no countdown runs on the email step")
            finish(f)
        }

    // --- Checking the code ---

    @Test
    fun theCodeInputKeepsOnlySixDigits() =
        runTest(dispatcher) {
            val f = fixture()
            onCodeStep(f)

            send(f, SignInIntent.CodeChanged("12a3456789"))
            assertEquals("123456", assertIs<SignInUiState.Code>(f.step).code)
            finish(f)
        }

    @Test
    fun anIncompleteCodeIsNotSent() =
        runTest(dispatcher) {
            val f = fixture()
            onCodeStep(f)

            send(f, SignInIntent.SubmitCode("123"))
            assertTrue(f.repository.verifies.isEmpty())
            finish(f)
        }

    @Test
    fun verifyingAnExistingAccountSignsInAndSkipsTheNameStep() =
        runTest(dispatcher) {
            val f = fixture()
            onCodeStep(f)
            send(f, SignInIntent.SubmitCode("123456"))

            assertEquals(listOf<SignInEffect>(SignInEffect.SignedIn(isNewAccount = false)), f.effects)
            assertTrue(f.state.isFinished)
            assertFalse(f.state.holdsAccountPage)
            assertFalse(f.state.isBusy)
            assertTrue(f.names.name == null, "no name is stored for an existing account")
            finish(f)
        }

    @Test
    fun verifyingANewAccountShowsTheNameStepAndHoldsTheAccountPage() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onVerify = { _, _ -> DomainResult.Success(session(isNewAccount = true)) }
            onCodeStep(f)
            send(f, SignInIntent.SubmitCode("123456"))

            assertEquals(SignInUiState.NewAccount(TEST_EMAIL), f.step)
            assertTrue(f.state.holdsAccountPage)
            assertTrue(f.effects.isEmpty(), "not signed in for the screen until the name step is done")
            finish(f)
        }

    @Test
    fun theNameStepStoresTheNameOnTheDeviceAndFinishes() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onVerify = { _, _ -> DomainResult.Success(session(isNewAccount = true)) }
            onCodeStep(f)
            send(f, SignInIntent.SubmitCode("123456"))

            send(f, SignInIntent.NameChanged("  Sari Wulandari "), SignInIntent.ConsentChanged(true))
            assertEquals(SignInUiState.NewAccount(TEST_EMAIL, "  Sari Wulandari ", true), f.step)
            send(f, SignInIntent.ContinueNewAccount)

            assertEquals("Sari Wulandari", f.names.name)
            assertEquals(TEST_USER.userId, f.names.userId)
            assertEquals(listOf<SignInEffect>(SignInEffect.SignedIn(isNewAccount = true)), f.effects)
            assertFalse(f.state.holdsAccountPage)
            finish(f)
        }

    @Test
    fun theNameStepNeedsANameAndSurvivesAStorageFailure() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onVerify = { _, _ -> DomainResult.Success(session(isNewAccount = true)) }
            onCodeStep(f)
            send(f, SignInIntent.SubmitCode("123456"))

            send(f, SignInIntent.NameChanged("   "), SignInIntent.ContinueNewAccount)
            assertTrue(f.effects.isEmpty())

            f.names.failOnSet = true
            send(f, SignInIntent.NameChanged("Sari"), SignInIntent.ContinueNewAccount)
            assertEquals(listOf<SignInEffect>(SignInEffect.SignedIn(isNewAccount = true)), f.effects)
            finish(f)
        }

    @Test
    fun changeEmailIsNotPossibleOnTheNameStep() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onVerify = { _, _ -> DomainResult.Success(session(isNewAccount = true)) }
            onCodeStep(f)
            send(f, SignInIntent.SubmitCode("123456"))

            send(f, SignInIntent.ChangeEmail)
            assertIs<SignInUiState.NewAccount>(f.step)
            finish(f)
        }

    @Test
    fun aWrongCodeShowsTheAttemptsLeftFromTheServer() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onVerify = { _, _ -> problem(400, ErrorCode.WRONG_CODE, attemptsLeft = 2) }
            onCodeStep(f)
            elapse(5_000)
            send(f, SignInIntent.SubmitCode("654321"))

            val wrong = assertIs<SignInUiState.WrongCode>(f.step)
            assertEquals(2, wrong.attemptsLeft)
            assertEquals("654321", wrong.code)
            assertEquals(55, wrong.resendInSeconds)
            assertFalse(f.state.isBusy)

            f.repository.onVerify = { _, _ -> problem(400, ErrorCode.WRONG_CODE, attemptsLeft = 1) }
            send(f, SignInIntent.CodeChanged("65432"))
            assertIs<SignInUiState.Code>(f.step)
            send(f, SignInIntent.CodeChanged("654322"), SignInIntent.SubmitCode("654322"))
            assertEquals(1, assertIs<SignInUiState.WrongCode>(f.step).attemptsLeft)
            finish(f)
        }

    @Test
    fun aWrongCodeWithoutAttemptsLeftCountsThemLocally() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onVerify = { _, _ -> problem(400, ErrorCode.WRONG_CODE) }
            onCodeStep(f)

            send(f, SignInIntent.SubmitCode("111111"))
            assertEquals(SignInPolicy.MAX_ATTEMPTS - 1, assertIs<SignInUiState.WrongCode>(f.step).attemptsLeft)
            send(f, SignInIntent.SubmitCode("111112"))
            assertEquals(SignInPolicy.MAX_ATTEMPTS - 2, assertIs<SignInUiState.WrongCode>(f.step).attemptsLeft)
            finish(f)
        }

    @Test
    fun anExpiredChallengeShowsCodeExpiredWithTheLastCode() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onVerify = { _, _ -> problem(410, ErrorCode.CHALLENGE_EXPIRED) }
            onCodeStep(f)
            send(f, SignInIntent.SubmitCode("000000"))

            assertEquals(SignInUiState.CodeExpired(TEST_EMAIL, "000000"), f.step)
            assertNull(f.state.error)
            finish(f)
        }

    @Test
    fun aLockedChallengeShowsCodeExpiredWithTheLastCode() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onVerify = { _, _ -> problem(410, ErrorCode.CHALLENGE_LOCKED) }
            onCodeStep(f)
            send(f, SignInIntent.SubmitCode("111111"))

            assertEquals(SignInUiState.CodeExpired(TEST_EMAIL, "111111"), f.step)
            finish(f)
        }

    @Test
    fun aNetworkFailureWhileCheckingKeepsTheCodeAndAllowsARetry() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onVerify = { _, _ -> DomainResult.NetworkError }
            onCodeStep(f)
            send(f, SignInIntent.SubmitCode("123456"))

            assertEquals("123456", assertIs<SignInUiState.Code>(f.step).code)
            assertEquals(SignInError.Network, f.state.error)
            assertFalse(f.state.isBusy)

            f.repository.onVerify = { _, _ -> DomainResult.Success(session()) }
            send(f, SignInIntent.SubmitCode("123456"))
            assertEquals(listOf<SignInEffect>(SignInEffect.SignedIn(isNewAccount = false)), f.effects)
            finish(f)
        }

    @Test
    fun aRateLimitedCheckShowsTheCountdownLine() =
        runTest(dispatcher) {
            val f = fixture()
            f.repository.onVerify = { _, _ -> problem(429, ErrorCode.RATE_LIMITED, retryAfter = 5) }
            onCodeStep(f)
            send(f, SignInIntent.SubmitCode("123456"))

            assertEquals(SignInError.RateLimited(5), f.state.error)
            assertIs<SignInUiState.Code>(f.step)
            elapse(5_000)
            assertNull(f.state.error)
            finish(f)
        }

    @Test
    fun theAutoSubmitAndTheButtonTogetherCheckTheCodeOnce() =
        runTest(dispatcher) {
            val f = fixture()
            val gate = CompletableDeferred<Unit>()
            f.repository.onVerify = { _, _ ->
                gate.await()
                DomainResult.Success(session())
            }
            onCodeStep(f)
            send(f, SignInIntent.SubmitCode("123456"), SignInIntent.SubmitCode("123456"))
            gate.complete(Unit)
            runCurrent()

            assertEquals(1, f.repository.verifies.size)
            assertEquals(1, f.effects.size)
            finish(f)
        }

    // --- Lifecycle ---

    @Test
    fun leavingTheScreenCancelsTheCountdowns() =
        runTest(dispatcher) {
            val f = fixture()
            onCodeStep(f)
            elapse(2_000)
            val before = f.step

            f.viewModel.clear()
            elapse(30_000)
            assertEquals(before, f.step)
        }

    @Test
    fun leavingTheScreenWhileACheckRunsDropsItsResult() =
        runTest(dispatcher) {
            val f = fixture()
            val gate = CompletableDeferred<Unit>()
            f.repository.onVerify = { _, _ ->
                gate.await()
                DomainResult.Success(session())
            }
            onCodeStep(f)
            send(f, SignInIntent.SubmitCode("123456"))
            assertTrue(f.state.isBusy)

            f.viewModel.clear()
            gate.complete(Unit)
            runCurrent()
            assertTrue(f.effects.isEmpty())
        }

    @Test
    fun aSignOutDuringTheCheckDropsTheLateSignInAndEndsItsSession() =
        runTest(dispatcher) {
            val f = fixture()
            val gate = CompletableDeferred<Unit>()
            f.repository.onVerify = { _, _ ->
                gate.await()
                DomainResult.Success(session())
            }
            onCodeStep(f)
            send(f, SignInIntent.SubmitCode("123456"))
            assertTrue(f.state.isBusy)

            // A sign-out (SignOutUseCase) bumps the generation, then the late answer arrives.
            f.generation.bump()
            gate.complete(Unit)
            runCurrent()

            assertEquals(1, f.repository.logouts, "the session the late answer started is ended again")
            assertTrue(f.effects.isEmpty())
            assertFalse(f.state.isFinished)
            assertFalse(f.state.isBusy)
            finish(f)
        }

    @Test
    fun theSessionEndingResetsTheFlowToAFreshEmailStep() =
        runTest(dispatcher) {
            val f = fixture()
            f.observer.current.value = SessionPhase.SignedIn
            runCurrent()
            send(f, SignInIntent.EmailChanged(TEST_EMAIL))
            onCodeStep(f)

            f.observer.current.value = SessionPhase.SignedOut
            runCurrent()

            assertEquals(SignInState(), f.state)
            finish(f)
        }

    @Test
    fun theColdStartPhasesDoNotResetTheFlow() =
        runTest(dispatcher) {
            val f = fixture()
            f.observer.current.value = SessionPhase.Restoring
            send(f, SignInIntent.EmailChanged(TEST_EMAIL))
            f.observer.current.value = SessionPhase.SignedOut
            runCurrent()

            assertEquals(SignInUiState.Email(TEST_EMAIL), f.step)
            finish(f)
        }
}
