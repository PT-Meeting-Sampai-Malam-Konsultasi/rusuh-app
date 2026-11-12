package com.rusuh.app.data.repository

import com.rusuh.app.data.network.session.SessionManager
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.AuthSession
import com.rusuh.app.domain.model.AuthUser
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import com.rusuh.app.domain.model.OtpChallenge
import com.rusuh.app.domain.model.UserLocale
import com.rusuh.app.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive

/**
 * Demo [AuthRepository] for [com.rusuh.app.di.BackendMode.Stub]: no network, in-memory accounts, and a REAL
 * [SessionManager] over an in-memory token store (placeholder tokens, never written to the secure store), so the
 * signed-in state, logout and the session-expiry flow behave as in
 * the real app.
 *
 * Codes: [CODE_OK] signs in, [CODE_EXPIRED] answers 410 CHALLENGE_EXPIRED, any other code is a 400 WRONG_CODE with
 * `attemptsLeft` counting down from [MAX_ATTEMPTS]; at zero the challenge is locked (410 CHALLENGE_LOCKED). An
 * unknown challenge id is a 410 CHALLENGE_EXPIRED. The first sign-in of an email creates its account
 * (`isNewAccount = true`); without a `fullName` at request time the user has no name, so the UI asks for it and
 * requests a new code with the name. Later sign-ins of that email are `isNewAccount = false`.
 *
 * shortcut: not persisted (a new process forgets the accounts), no resend cooldown or rate limit.
 */
class StubAuthRepository(
    private val session: SessionManager,
    private val clock: ServerClock,
) : AuthRepository {
    private class Challenge(
        val email: String,
        val fullName: String?,
        val locale: UserLocale?,
        val expiresAtMillis: Long,
        var attemptsLeft: Int = MAX_ATTEMPTS,
    )

    private val lock = Mutex()
    private val challenges = mutableMapOf<String, Challenge>()
    private val accounts = mutableMapOf<String, AuthUser>()
    private var counter = 0
    private var currentEmail: String? = null

    override suspend fun requestOtp(
        email: String,
        fullName: String?,
        locale: UserLocale?,
    ): DomainResult<OtpChallenge> =
        lock.withLock {
            if ('@' !in email.trim()) return@withLock problem(400, ErrorCode.VALIDATION_FAILED)
            val id = "ch_stub_${++counter}"
            val expiresAt = clock.nowMillis() + CHALLENGE_TTL_MILLIS
            challenges[id] = Challenge(email.trim(), fullName?.takeIf { it.isNotBlank() }, locale, expiresAt)
            DomainResult.Success(OtpChallenge(id, expiresAt, RESEND_AFTER_SECONDS))
        }

    override suspend fun verifyOtp(
        challengeId: String,
        code: String,
    ): DomainResult<AuthSession> =
        lock.withLock {
            val challenge = challenges[challengeId]
            when {
                challenge == null || code == CODE_EXPIRED || clock.nowMillis() > challenge.expiresAtMillis ->
                    problem(410, ErrorCode.CHALLENGE_EXPIRED)
                challenge.attemptsLeft <= 0 -> problem(410, ErrorCode.CHALLENGE_LOCKED)
                code != CODE_OK -> wrongCode(challenge)
                else -> signIn(challengeId, challenge)
            }
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun logout(): DomainResult<Unit> {
        lock.withLock { currentEmail = null }
        return try {
            session.endSession()
            DomainResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            DomainResult.TechnicalError // same as the real repository when the local clear fails
        }
    }

    override suspend fun me(): DomainResult<AuthUser> =
        lock.withLock {
            // Not signed in any more (logout or a session expiry) means unauthorized, like the server.
            currentEmail?.takeIf {
                session.isSignedIn.value
            }?.let { accounts[it] }?.let { DomainResult.Success(it) } ?: problem(401, ErrorCode.UNAUTHORIZED)
        }

    override suspend fun updateProfile(
        fullName: String,
        locale: UserLocale?,
    ): DomainResult<AuthUser> =
        lock.withLock {
            val user = currentEmail?.let(accounts::get)
            if (user == null || !session.isSignedIn.value) {
                problem(401, ErrorCode.UNAUTHORIZED)
            } else {
                user.copy(fullName = fullName.trim(), locale = locale ?: user.locale).also { accounts[user.email] = it }
                    .let { DomainResult.Success(it) }
            }
        }

    private fun wrongCode(challenge: Challenge): DomainResult<Nothing> {
        challenge.attemptsLeft--
        return if (challenge.attemptsLeft <= 0) {
            problem(410, ErrorCode.CHALLENGE_LOCKED)
        } else {
            problem(400, ErrorCode.WRONG_CODE, mapOf("attemptsLeft" to JsonPrimitive(challenge.attemptsLeft)))
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun signIn(
        challengeId: String,
        challenge: Challenge,
    ): DomainResult<AuthSession> {
        challenges.remove(challengeId)
        val existing = accounts[challenge.email]
        val user =
            existing
                ?: AuthUser("usr_stub_${accounts.size + 1}", challenge.email, challenge.fullName, challenge.locale)
                    .also { accounts[challenge.email] = it }
        return try {
            session.startSession("stub-access-$counter", "stub-refresh-$counter")
            currentEmail = challenge.email
            DomainResult.Success(AuthSession(user, isNewAccount = existing == null, EXPIRES_IN_SECONDS))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            DomainResult.TechnicalError // same as the real repository when the tokens cannot be stored
        }
    }

    private fun problem(
        status: Int,
        code: ErrorCode,
        extras: Map<String, JsonPrimitive> = emptyMap(),
    ) = DomainResult.Error(message = "stub", code = status, errorCode = code, extras = extras)

    companion object {
        /** The code that signs in. */
        const val CODE_OK = "123456"

        /** The code that answers "expired". */
        const val CODE_EXPIRED = "000000"

        const val MAX_ATTEMPTS = 5
        private const val RESEND_AFTER_SECONDS = 60
        private const val CHALLENGE_TTL_MILLIS = 10 * 60 * 1_000L
        private const val EXPIRES_IN_SECONDS = 900
    }
}
