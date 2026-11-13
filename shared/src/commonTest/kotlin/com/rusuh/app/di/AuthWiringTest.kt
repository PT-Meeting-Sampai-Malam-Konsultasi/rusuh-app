package com.rusuh.app.di

import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.data.network.session.SessionManager
import com.rusuh.app.data.storage.AppPreferences
import com.rusuh.app.data.storage.FakeAppPreferences
import com.rusuh.app.data.storage.FakeSecureStore
import com.rusuh.app.data.storage.SecureStore
import com.rusuh.app.domain.model.AuthSession
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.OtpChallenge
import com.rusuh.app.domain.repository.AuthRepository
import com.rusuh.app.domain.session.SessionPhase
import com.rusuh.app.domain.usecase.ObserveSessionUseCase
import com.rusuh.app.feature.account.AccountViewModel
import com.rusuh.app.feature.auth.SignInViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthWiringTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun koin(): Koin =
        koinApplication {
            allowOverride(true)
            modules(
                module {
                    single { AppEnvironment.Mock }
                    single<SecureStore> { FakeSecureStore() }
                    single<AppPreferences> { FakeAppPreferences() }
                },
                storageModule,
                coreModule(),
                *dataModules(BackendMode.Stub, true) { }.toTypedArray(),
                appModule,
            )
        }.koin

    @Test
    fun theViewModelsResolveWithTheirUseCases() {
        val koin = koin()
        val signIn = koin.get<SignInViewModel>()
        val account = koin.get<AccountViewModel>()

        assertNotSame(signIn, koin.get<SignInViewModel>(), "a factory: one per route")
        signIn.clear()
        account.clear()
    }

    @Test
    fun oneGenerationCounterIsSharedByTheSignInAndTheSignOut() {
        val koin = koin()
        assertSame(
            koin.get<com.rusuh.app.domain.usecase.AuthGeneration>(),
            koin.get<com.rusuh.app.domain.usecase.AuthGeneration>(),
        )
    }

    @Test
    fun theStubSignInFlowEndsSignedInThroughTheSessionObserver() =
        runTest(dispatcher) {
            val koin = koin()
            val auth = koin.get<AuthRepository>()
            val challenge = assertIs<DomainResult.Success<OtpChallenge>>(auth.requestOtp("stub.user@example.test")).data
            val verified = assertIs<DomainResult.Success<AuthSession>>(auth.verifyOtp(challenge.challengeId, "123456"))
            runCurrent()

            assertTrue(verified.data.isNewAccount)
            assertTrue(koin.get<SessionManager>().isSignedIn.value)
            assertEquals(SessionPhase.SignedIn, koin.get<ObserveSessionUseCase>().phase().first())
        }
}
