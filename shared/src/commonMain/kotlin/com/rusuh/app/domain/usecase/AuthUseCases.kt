package com.rusuh.app.domain.usecase

import com.rusuh.app.domain.model.AccountSummary
import com.rusuh.app.domain.model.AuthSession
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.OtpChallenge
import com.rusuh.app.domain.model.UserLocale
import com.rusuh.app.domain.repository.AuthRepository
import com.rusuh.app.domain.repository.DisplayNameStore
import com.rusuh.app.domain.session.SessionObserver
import com.rusuh.app.domain.session.SessionPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/**
 * Counts the sign-outs, so a sign-in that finishes after one can tell it is stale: [VerifyOtpUseCase] remembers the
 * value when it starts and [SignOutUseCase] bumps it first. One instance for the app (Koin `single`).
 *
 * Only the main dispatcher touches it, so a plain volatile counter is enough.
 */
class AuthGeneration {
    @Volatile
    private var value = 0

    fun current(): Int = value

    fun bump() {
        value += 1
    }
}

/** Sends the sign-in code. The consent version is added by the repository. */
class RequestOtpUseCase(
    private val repository: AuthRepository,
    private val appLocale: () -> UserLocale? = { null },
) {
    /** [fullName] only when the email has no account yet and the name is known (the app asks after the code). */
    suspend operator fun invoke(
        email: String,
        fullName: String? = null,
    ): DomainResult<OtpChallenge> = repository.requestOtp(email.trim(), fullName, appLocale())
}

/** How a [VerifyOtpUseCase] call ended. */
sealed interface VerifyOtpOutcome {
    /** The repository answered; read [result] (success means the session is signed in). */
    data class Completed(val result: DomainResult<AuthSession>) : VerifyOtpOutcome

    /**
     * A sign-out happened while the code was being checked. The answer was dropped, and when it would have signed
     * in, that session was ended again, so the user is not signed back in against their will.
     */
    data object Superseded : VerifyOtpOutcome
}

/** Checks the 6-digit code. Guards against the sign-out race with [AuthGeneration] (see `AuthRepository.logout`). */
class VerifyOtpUseCase(
    private val repository: AuthRepository,
    private val generation: AuthGeneration,
) {
    suspend operator fun invoke(
        challengeId: String,
        code: String,
    ): VerifyOtpOutcome {
        val started = generation.current()
        val result = repository.verifyOtp(challengeId, code)
        if (generation.current() == started) return VerifyOtpOutcome.Completed(result)
        // The check started a session after the sign-out: end it again, even if this caller is cancelled now.
        if (result is DomainResult.Success) withContext(NonCancellable) { repository.logout() }
        return VerifyOtpOutcome.Superseded
    }
}

/**
 * Signs out. Order: the generation moves on (in-flight sign-ins become stale), the local name is dropped, then the
 * repository clears the local session first and revokes on the server (bounded). Navigate on the session phase,
 * not on this result.
 */
class SignOutUseCase(
    private val repository: AuthRepository,
    private val generation: AuthGeneration,
    private val displayNames: DisplayNameStore,
) {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend operator fun invoke(): DomainResult<Unit> {
        generation.bump()
        try {
            displayNames.clear()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // Best effort: a name that stays is only a display fallback and is bound to its user id.
        }
        return repository.logout()
    }
}

/** `GET /me` plus the device-only name as a fallback (shortcut: the contract has no profile update). */
class GetProfileUseCase(
    private val repository: AuthRepository,
    private val displayNames: DisplayNameStore,
) {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend operator fun invoke(): DomainResult<AccountSummary> =
        when (val me = repository.me()) {
            is DomainResult.Success -> {
                val user = me.data
                val stored =
                    if (user.fullName.isNullOrBlank()) {
                        try {
                            displayNames.get(user.userId)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Throwable) {
                            null
                        }
                    } else {
                        null
                    }
                val name = user.fullName?.takeIf { it.isNotBlank() } ?: stored
                DomainResult.Success(AccountSummary(user.userId, user.email, name))
            }
            is DomainResult.Error -> me
            DomainResult.Empty -> DomainResult.TechnicalError
            DomainResult.NetworkError -> DomainResult.NetworkError
            DomainResult.TechnicalError -> DomainResult.TechnicalError
        }
}

/** Keeps the name typed in the new-account step on this device (shortcut, see [DisplayNameStore]). */
class SaveDisplayNameUseCase(
    private val displayNames: DisplayNameStore,
    private val repository: AuthRepository,
) {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend operator fun invoke(
        userId: String,
        name: String,
    ) {
        val trimmed = name.trim()
        displayNames.set(userId, trimmed)
        try {
            repository.updateProfile(trimmed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // The local name remains available if the server cannot save it.
        }
    }
}

/** The session as the screens see it. */
class ObserveSessionUseCase(
    private val observer: SessionObserver,
) {
    val currentPhase get() = observer.currentPhase

    fun phase(): Flow<SessionPhase> = observer.phase

    /** One item each time the session expired (refresh token rejected). */
    fun expired(): Flow<Unit> = observer.expired

    /** Bounded wait for the cold-start restore. */
    suspend fun awaitSignedIn(): Boolean = observer.awaitSignedIn()
}
