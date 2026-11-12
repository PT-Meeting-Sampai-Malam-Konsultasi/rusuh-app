package com.rusuh.app.domain.usecase

import com.rusuh.app.data.cache.AuthCache
import com.rusuh.app.data.storage.FakeAppPreferences
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.UserLocale
import com.rusuh.app.feature.auth.FakeAuthRepository
import com.rusuh.app.feature.auth.FakeDisplayNames
import com.rusuh.app.feature.auth.TEST_EMAIL
import com.rusuh.app.feature.auth.TEST_USER
import com.rusuh.app.feature.auth.session
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AuthUseCasesTest {
    @Test
    fun requestOtpTrimsTheEmailAndAddsTheAppLocale() =
        runTest {
            val repository = FakeAuthRepository()
            val result = RequestOtpUseCase(repository) { UserLocale.English }("  $TEST_EMAIL ")

            assertIs<DomainResult.Success<*>>(result)
            val request = repository.requests.single()
            assertEquals(TEST_EMAIL, request.email)
            assertEquals(UserLocale.English, request.locale)
            assertNull(request.fullName)
        }

    @Test
    fun verifyOtpPassesTheRepositoryAnswerThroughWhenNothingSignedOut() =
        runTest {
            val repository = FakeAuthRepository()
            val outcome = VerifyOtpUseCase(repository, AuthGeneration())("ch_1", "123456")

            assertEquals(VerifyOtpOutcome.Completed(DomainResult.Success(session())), outcome)
            assertEquals(0, repository.logouts)
        }

    @Test
    fun verifyOtpEndsASessionThatStartedAfterASignOut() =
        runTest {
            val repository = FakeAuthRepository()
            val generation = AuthGeneration()
            val gate = CompletableDeferred<Unit>()
            repository.onVerify = { _, _ ->
                gate.await()
                DomainResult.Success(session())
            }
            val outcome = async { VerifyOtpUseCase(repository, generation)("ch_1", "123456") }
            runCurrent()

            generation.bump()
            gate.complete(Unit)

            assertEquals(VerifyOtpOutcome.Superseded, outcome.await())
            assertEquals(1, repository.logouts)
        }

    @Test
    fun verifyOtpDropsAStaleFailureWithoutLoggingOut() =
        runTest {
            val repository = FakeAuthRepository()
            val generation = AuthGeneration()
            val gate = CompletableDeferred<Unit>()
            repository.onVerify = { _, _ ->
                gate.await()
                DomainResult.NetworkError
            }
            val outcome = async { VerifyOtpUseCase(repository, generation)("ch_1", "123456") }
            runCurrent()
            generation.bump()
            gate.complete(Unit)

            assertEquals(VerifyOtpOutcome.Superseded, outcome.await())
            assertEquals(0, repository.logouts)
        }

    @Test
    fun signOutBumpsTheGenerationDropsTheLocalNameAndLogsOut() =
        runTest {
            val repository = FakeAuthRepository()
            val generation = AuthGeneration()
            val names = FakeDisplayNames().also { it.set(TEST_USER.userId, "Sari Wulandari") }

            val result = SignOutUseCase(repository, generation, names)()

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(1, generation.current())
            assertNull(names.name)
            assertEquals(1, repository.logouts)
        }

    @Test
    fun signOutStillLogsOutWhenTheLocalNameCannotBeCleared() =
        runTest {
            val repository = FakeAuthRepository()
            val failing =
                object : com.rusuh.app.domain.repository.DisplayNameStore {
                    override suspend fun get(userId: String): String? = null

                    override suspend fun set(
                        userId: String,
                        name: String,
                    ) = Unit

                    override suspend fun clear() = error("storage failed")
                }

            SignOutUseCase(repository, AuthGeneration(), failing)()

            assertEquals(1, repository.logouts)
        }

    @Test
    fun getProfileKeepsAServerErrorAndMapsEmptyToTechnical() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = GetProfileUseCase(repository, FakeDisplayNames())

            repository.onMe = { DomainResult.NetworkError }
            assertEquals(DomainResult.NetworkError, useCase())
            repository.onMe = { DomainResult.Empty }
            assertEquals(DomainResult.TechnicalError, useCase())
        }

    @Test
    fun getProfilePrefersTheServerNameOverTheDeviceName() =
        runTest {
            val repository = FakeAuthRepository()
            val names = FakeDisplayNames().also { it.set(TEST_USER.userId, "Device Name") }
            repository.onMe = { DomainResult.Success(TEST_USER.copy(fullName = "Server Name")) }

            val profile = assertIs<DomainResult.Success<*>>(GetProfileUseCase(repository, names)()).data
            assertEquals("Server Name", (profile as com.rusuh.app.domain.model.AccountSummary).name)
        }

    // --- The device-only name (AuthCache over AppPreferences) ---

    @Test
    fun theDisplayNameBelongsToTheUserItWasStoredFor() =
        runTest {
            val prefs = FakeAppPreferences()
            val cache = AuthCache(prefs)
            SaveDisplayNameUseCase(cache, FakeAuthRepository())("usr_1", "  Sari Wulandari ")

            assertEquals("Sari Wulandari", cache.get("usr_1"))
            assertNull(cache.get("usr_2"))
            assertEquals(setOf("auth_display_name", "auth_display_name_user"), prefs.entries.keys)
        }

    @Test
    fun clearingTheDisplayNameRemovesBothKeys() =
        runTest {
            val prefs = FakeAppPreferences()
            val cache = AuthCache(prefs)
            cache.set("usr_1", "Sari Wulandari")

            cache.clear()

            assertNull(cache.get("usr_1"))
            assertEquals(emptySet(), prefs.entries.keys)
        }
}
