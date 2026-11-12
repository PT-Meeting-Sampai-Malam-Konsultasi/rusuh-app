package com.rusuh.app.data.repository

import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.session.RefreshOutcome
import com.rusuh.app.data.network.session.SessionManager
import com.rusuh.app.data.network.session.TokenRefresher
import com.rusuh.app.data.remote.AuthApiService
import com.rusuh.app.data.remote.dto.CONSENT_VERSION
import com.rusuh.app.data.remote.dto.RequestOtpDto
import com.rusuh.app.data.remote.dto.SessionDto
import com.rusuh.app.data.remote.dto.VerifyOtpDto
import com.rusuh.app.data.remote.mapper.toDomain
import com.rusuh.app.domain.model.AuthSession
import com.rusuh.app.domain.model.AuthUser
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import com.rusuh.app.domain.model.OtpChallenge
import com.rusuh.app.domain.model.UserLocale
import com.rusuh.app.domain.repository.AuthRepository
import com.rusuh.app.domain.repository.DeviceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [AuthRepository] over the auth endpoints. Tokens, codes and emails are never logged.
 *
 * - The code request and check carry the device token when there is one: registration
 *   ([DeviceRepository.ensureRegistered]) is tried first but is best effort, so a registration error (the live
 *   backend has no `POST /devices` yet) never blocks the call. Only a NetworkError from the registration is returned
 *   as is. A 401 from the auth call itself forces one new registration and one retry; these calls are session-less,
 *   so that 401 never expires the session.
 * - A successful check hands the tokens to the [SessionManager], which stores them exactly like the refresh layer
 *   does and turns the signed-in state on.
 * - [logoutTimeout] bounds the server call of [logout].
 */
@Suppress("TooManyFunctions") // the four interface calls plus the small private steps of the logout and the gate
class RealAuthRepository(
    private val api: AuthApiService,
    private val devices: DeviceRepository,
    private val session: SessionManager,
    private val refresher: TokenRefresher,
    private val logoutTimeout: Duration = DEFAULT_LOGOUT_TIMEOUT,
) : AuthRepository {
    override suspend fun requestOtp(
        email: String,
        fullName: String?,
        locale: UserLocale?,
    ): DomainResult<OtpChallenge> {
        val body =
            RequestOtpDto(
                email = email.trim(),
                fullName = fullName?.trim()?.takeIf { it.isNotEmpty() },
                consentVersion = CONSENT_VERSION,
                locale = locale?.wire,
            )
        // A 202 without a challenge id cannot be used: a client-side problem, not an empty result.
        return deviceGated({ api.requestOtp(body) }) { it.toDomainResult { dto -> dto.toDomain() }.emptyIsTechnical() }
    }

    // A verifyOtp that completes after a logout signs the user back in: VerifyOtpUseCase guards it with a generation
    // counter (bumped by SignOutUseCase) and ends the session again.
    override suspend fun verifyOtp(
        challengeId: String,
        code: String,
    ): DomainResult<AuthSession> {
        val body = VerifyOtpDto(challengeId, code.trim())
        return deviceGated({ api.verifyOtp(body) }) { result ->
            // Without an access token the answer cannot start a session: a client-side problem.
            when (val verified = result.toDomainResult { dto -> dto.takeIf { it.accessToken.isNotBlank() } }) {
                is DomainResult.Success -> startSession(verified.data)
                // 409 on verify can only be the older contract's wrong code (400 WRONG_CODE today).
                is DomainResult.Error ->
                    if (verified.code == HTTP_CONFLICT && verified.errorCode == ErrorCode.UNKNOWN) {
                        verified.copy(errorCode = ErrorCode.WRONG_CODE)
                    } else {
                        verified
                    }
                DomainResult.NetworkError -> DomainResult.NetworkError
                DomainResult.Empty, DomainResult.TechnicalError -> DomainResult.TechnicalError
            }
        }
    }

    /**
     * Signs out. The local session is cleared FIRST and non-cancellably (so the UI is signed out at once and never
     * waits for the network); the result only describes the server revoke attempt, the local sign-out always
     * happened (read `SessionManager.isSignedIn`). Snapshots of the access and refresh token are taken before the
     * clear. The revoke runs inside [logoutTimeout]:
     * - with an access token: `POST /auth/logout`; a 401 (expired token) or no access token (cold start) gets ONE
     *   `POST /auth/refresh` with the snapshot refresh token (session-less: no `SessionExpired`, the result is not
     *   stored) and the logout is sent with that fresh access token;
     * - a refresh the server rejects, or no refresh token at all, means there is nothing to revoke: success;
     * - a 401 on the logout itself is not an error (the token family is gone either way).
     * No server call when not signed in. Timeout or an unreachable server is [DomainResult.NetworkError]: not
     * revoked and not worth retrying (the local session is gone; a rotated refresh token may stay valid on the server
     * until it expires). [DomainResult.NetworkError] covers both calls (refresh and logout) under the one bound. The
     * wait for a cold-start restore ([SessionManager.awaitSignedIn], at most 5 s if the storage read hangs) happens
     * before the clear. If clearing
     * storage threw, the result is [DomainResult.TechnicalError] (the refresh token may remain on disk: retry).
     */
    override suspend fun logout(): DomainResult<Unit> {
        val wasSignedIn = session.awaitSignedIn()
        val bearer = session.currentAccessToken()
        val refreshToken = session.currentRefreshToken()
        val cleared = withContext(NonCancellable) { clearLocally() }
        val remote: DomainResult<Unit> =
            if (wasSignedIn && (bearer != null || refreshToken != null)) {
                bounded { revoke(bearer, refreshToken) }
            } else {
                DomainResult.Success(Unit)
            }
        return if (cleared) remote else DomainResult.TechnicalError
    }

    private suspend fun bounded(block: suspend () -> DomainResult<Unit>): DomainResult<Unit> =
        // An infinite timeout means "no bound" (tests): do not even schedule one.
        if (logoutTimeout.isInfinite()) {
            block()
        } else {
            withTimeoutOrNull(logoutTimeout) { block() } ?: DomainResult.NetworkError
        }

    private suspend fun revoke(
        bearer: String?,
        refreshToken: String?,
    ): DomainResult<Unit> {
        val first = bearer?.let { api.logout(it).toDomainResult { Unit } }
        return if (first != null && !first.isUnauthorized()) {
            first
        } else {
            when (val fresh = freshAccessToken(refreshToken)) {
                is Fresh.Token -> api.logout(fresh.value).toDomainResult { Unit }.unauthorizedIsDone()
                Fresh.Nothing -> DomainResult.Success(Unit)
                // Nothing was revoked: never report success, whatever the first logout answered.
                Fresh.Unreachable -> DomainResult.NetworkError
            }
        }
    }

    private sealed interface Fresh {
        class Token(val value: String) : Fresh

        /** Nothing to revoke: no refresh token, or the server already rejected it. */
        data object Nothing : Fresh

        data object Unreachable : Fresh
    }

    private suspend fun freshAccessToken(refreshToken: String?): Fresh {
        if (refreshToken == null) return Fresh.Nothing
        return when (val outcome = refresher.refresh(refreshToken)) {
            is RefreshOutcome.Refreshed -> Fresh.Token(outcome.accessToken)
            RefreshOutcome.Rejected -> Fresh.Nothing
            RefreshOutcome.Unavailable -> Fresh.Unreachable
        }
    }

    private fun DomainResult<Unit>.isUnauthorized() = this is DomainResult.Error && code == HTTP_UNAUTHORIZED

    private fun DomainResult<Unit>.unauthorizedIsDone(): DomainResult<Unit> =
        if (isUnauthorized()) DomainResult.Success(Unit) else this

    override suspend fun me(): DomainResult<AuthUser> = api.getMe().toDomainResult { it.toDomain() }.emptyIsTechnical()

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun clearLocally(): Boolean =
        try {
            session.endSession()
            true
        } catch (failure: Throwable) {
            false // the signed-in flag is already off, see SessionManager.endSession
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun startSession(dto: SessionDto): DomainResult<AuthSession> {
        val user = dto.toDomain() ?: return DomainResult.TechnicalError
        return try {
            session.startSession(dto.accessToken, dto.refreshToken?.takeIf { it.isNotBlank() })
            DomainResult.Success(user)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            DomainResult.TechnicalError // tokens could not be stored: not signed in
        }
    }

    private suspend fun <T, R> deviceGated(
        call: suspend () -> ApiResult<T>,
        map: suspend (ApiResult<T>) -> DomainResult<R>,
    ): DomainResult<R> {
        if (register(forceRefresh = false) == DomainResult.NetworkError) return DomainResult.NetworkError
        var result = call()
        var offline = false
        if (result is ApiResult.Error && result.code == HTTP_UNAUTHORIZED) {
            val again = register(forceRefresh = true)
            offline = again == DomainResult.NetworkError
            // Retry only when the forced registration worked: after an error the retry would fail the same way.
            if (again is DomainResult.Success) result = call()
        }
        return if (offline) DomainResult.NetworkError else map(result)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun register(forceRefresh: Boolean): DomainResult<Unit> =
        try {
            devices.ensureRegistered(forceRefresh)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            DomainResult.TechnicalError
        }

    private fun <T> DomainResult<T>.emptyIsTechnical(): DomainResult<T> =
        if (this is DomainResult.Empty) DomainResult.TechnicalError else this

    private companion object {
        val DEFAULT_LOGOUT_TIMEOUT = 5.seconds
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_CONFLICT = 409
    }
}
