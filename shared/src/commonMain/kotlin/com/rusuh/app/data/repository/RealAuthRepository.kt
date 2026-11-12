package com.rusuh.app.data.repository

import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.network.session.SessionManager
import com.rusuh.app.data.remote.AuthApiService
import com.rusuh.app.data.remote.dto.CONSENT_VERSION
import com.rusuh.app.data.remote.dto.RequestOtpDto
import com.rusuh.app.data.remote.dto.SessionDto
import com.rusuh.app.data.remote.dto.VerifyOtpDto
import com.rusuh.app.data.remote.mapper.toDomain
import com.rusuh.app.domain.model.AuthSession
import com.rusuh.app.domain.model.AuthUser
import com.rusuh.app.domain.model.DomainResult
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
class RealAuthRepository(
    private val api: AuthApiService,
    private val devices: DeviceRepository,
    private val session: SessionManager,
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

    override suspend fun verifyOtp(
        challengeId: String,
        code: String,
    ): DomainResult<AuthSession> {
        val body = VerifyOtpDto(challengeId, code.trim())
        return deviceGated({ api.verifyOtp(body) }) { result ->
            // Without an access token the answer cannot start a session: a client-side problem.
            when (val verified = result.toDomainResult { dto -> dto.takeIf { it.accessToken.isNotBlank() } }) {
                is DomainResult.Success -> startSession(verified.data)
                is DomainResult.Error -> verified
                DomainResult.NetworkError -> DomainResult.NetworkError
                DomainResult.Empty, DomainResult.TechnicalError -> DomainResult.TechnicalError
            }
        }
    }

    /**
     * Signs out. Order: snapshot the Bearer, clear the local session (so the UI is signed out at once and an offline
     * logout never waits), then revoke on the server with the snapshot, bounded by the logout timeout. No server call
     * when there was no session or no access token (a cold start before the first refresh). A failed local clear
     * (storage threw) is returned as [DomainResult.TechnicalError] after the server call was still tried: the app is
     * signed out in memory, but a refresh token may remain on disk, so the caller should offer a retry.
     */
    override suspend fun logout(): DomainResult<Unit> {
        val wasSignedIn = session.awaitSignedIn()
        val bearer = session.currentAccessToken()
        val cleared = withContext(NonCancellable) { clearLocally() }
        val remote: DomainResult<Unit> =
            if (wasSignedIn && bearer != null) {
                revoke(bearer)
            } else {
                DomainResult.Success(Unit)
            }
        return if (cleared) remote else DomainResult.TechnicalError
    }

    private suspend fun revoke(bearer: String): DomainResult<Unit> {
        suspend fun call() = api.logout(bearer).toDomainResult { Unit }
        // An infinite timeout means "no bound" (tests): do not even schedule one.
        return if (logoutTimeout.isInfinite()) {
            call()
        } else {
            withTimeoutOrNull(logoutTimeout) {
                call()
            } ?: DomainResult.NetworkError
        }
    }

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
            offline = register(forceRefresh = true) == DomainResult.NetworkError
            if (!offline) result = call()
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
    }
}
