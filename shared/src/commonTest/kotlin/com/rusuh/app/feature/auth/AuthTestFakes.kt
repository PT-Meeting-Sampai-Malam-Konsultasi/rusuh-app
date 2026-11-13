package com.rusuh.app.feature.auth

import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.AuthSession
import com.rusuh.app.domain.model.AuthUser
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import com.rusuh.app.domain.model.OtpChallenge
import com.rusuh.app.domain.model.UserLocale
import com.rusuh.app.domain.repository.AccountEmailStore
import com.rusuh.app.domain.repository.AuthRepository
import com.rusuh.app.domain.repository.DisplayNameStore
import com.rusuh.app.domain.session.SessionObserver
import com.rusuh.app.domain.session.SessionPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.json.JsonPrimitive

// Synthetic fixtures only: no real emails, names or tokens.

const val TEST_EMAIL = "test.user@example.test"

val TEST_USER = AuthUser(userId = "usr_test_1", email = TEST_EMAIL, fullName = null, locale = null)

fun challenge(
    id: String = "ch_test_1",
    expiresAtMillis: Long? = null,
    resendAfterSeconds: Int = 60,
) = OtpChallenge(id, expiresAtMillis, resendAfterSeconds)

fun session(
    isNewAccount: Boolean = false,
    user: AuthUser = TEST_USER,
) = AuthSession(user = user, isNewAccount = isNewAccount, accessTokenExpiresInSeconds = 900)

fun problem(
    status: Int,
    code: ErrorCode,
    attemptsLeft: Int? = null,
    retryAfter: Int? = null,
) = DomainResult.Error(
    message = "server text must never reach the screen",
    code = status,
    errorCode = code,
    extras = attemptsLeft?.let { mapOf("attemptsLeft" to JsonPrimitive(it)) } ?: emptyMap(),
    retryAfterSeconds = retryAfter,
)

/** Server clock that follows the test scheduler's virtual time. */
class TestClock(
    private val scheduler: TestCoroutineScheduler,
    private val startMillis: Long = START_MILLIS,
    override var isSynced: Boolean = true,
) : ServerClock {
    override fun nowMillis(): Long = startMillis + scheduler.currentTime

    companion object {
        const val START_MILLIS = 1_800_000_000_000L
    }
}

/** Scripted [AuthRepository]: every call goes through a replaceable lambda and is recorded. */
class FakeAuthRepository : AuthRepository {
    class Request(val email: String, val fullName: String?, val locale: UserLocale?)

    val requests = mutableListOf<Request>()
    val verifies = mutableListOf<Pair<String, String>>()
    var logouts = 0
    var meCalls = 0

    var onRequest: suspend (Request) -> DomainResult<OtpChallenge> = { DomainResult.Success(challenge()) }
    var onVerify: suspend (String, String) -> DomainResult<AuthSession> = { _, _ -> DomainResult.Success(session()) }
    var onLogout: suspend () -> DomainResult<Unit> = { DomainResult.Success(Unit) }
    var onMe: suspend () -> DomainResult<AuthUser> = { DomainResult.Success(TEST_USER) }

    override suspend fun requestOtp(
        email: String,
        fullName: String?,
        locale: UserLocale?,
    ): DomainResult<OtpChallenge> {
        val request = Request(email, fullName, locale)
        requests += request
        return onRequest(request)
    }

    override suspend fun verifyOtp(
        challengeId: String,
        code: String,
    ): DomainResult<AuthSession> {
        verifies += challengeId to code
        return onVerify(challengeId, code)
    }

    override suspend fun logout(): DomainResult<Unit> {
        logouts++
        return onLogout()
    }

    override suspend fun me(): DomainResult<AuthUser> {
        meCalls++
        return onMe()
    }

    override suspend fun updateProfile(
        fullName: String,
        locale: UserLocale?,
    ): DomainResult<AuthUser> =
        DomainResult.Success(TEST_USER.copy(fullName = fullName, locale = locale ?: TEST_USER.locale))
}

class FakeSessionObserver(initial: SessionPhase = SessionPhase.SignedOut) : SessionObserver {
    val current = MutableStateFlow(initial)
    override val currentPhase: SessionPhase get() = current.value
    val expiredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** The bounded wait for the cold-start restore: pending until a test completes it (false = it timed out). */
    val awaitGate = CompletableDeferred<Boolean>()
    var awaitCalls = 0

    override val phase: Flow<SessionPhase> = current.map { it }
    override val expired: Flow<Unit> = expiredEvents

    override suspend fun awaitSignedIn(): Boolean {
        awaitCalls++
        return awaitGate.await()
    }
}

class FakeDisplayNames : DisplayNameStore {
    var userId: String? = null
    var name: String? = null
    var failOnSet = false
    var clears = 0

    override suspend fun get(userId: String): String? = name.takeIf { this.userId == userId }

    override suspend fun set(
        userId: String,
        name: String,
    ) {
        check(!failOnSet) { "storage failed" }
        this.userId = userId
        this.name = name
    }

    override suspend fun clear() {
        clears++
        userId = null
        name = null
    }
}

class FakeAccountEmails : AccountEmailStore {
    var userId: String? = null
    var stored: String? = null
    var clears = 0

    override suspend fun email(): String? = stored

    override suspend fun saveEmail(
        userId: String,
        email: String,
    ) {
        this.userId = userId
        stored = email
    }

    override suspend fun clearEmail() {
        clears++
        stored = null
    }
}
