package com.rusuh.app.data.repository

import com.rusuh.app.data.network.session.SessionManager
import com.rusuh.app.data.remote.AuthApiService
import com.rusuh.app.data.remote.dto.RequestOtpDto
import com.rusuh.app.data.remote.dto.UpdateProfileDto
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

/**
 * [AuthRepository] over the auth endpoints. The code request and check need the device token, so they make sure
 * the install is registered first ([DeviceRepository.ensureRegistered]). A successful check hands the tokens to the
 * [SessionManager], which stores them exactly like the refresh layer does and turns the signed-in state on. Tokens,
 * codes and emails are never logged.
 */
class RealAuthRepository(
    private val api: AuthApiService,
    private val devices: DeviceRepository,
    private val session: SessionManager,
) : AuthRepository {
    override suspend fun requestOtp(
        email: String,
        fullName: String?,
        locale: UserLocale?,
    ): DomainResult<OtpChallenge> =
        withDeviceToken {
            val body =
                RequestOtpDto(
                    email = email,
                    fullName = fullName?.takeIf { it.isNotBlank() },
                    locale = locale?.wire,
                )
            // A 202 without a challenge id cannot be used: a client-side problem, not an empty result.
            api.requestOtp(body).toDomainResult { it.toDomain() }.emptyIsTechnical()
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun verifyOtp(
        challengeId: String,
        code: String,
    ): DomainResult<AuthSession> =
        withDeviceToken {
            // Without an access token or a user id the answer cannot start a session: a client-side problem.
            val verified =
                api.verifyOtp(VerifyOtpDto(challengeId, code)).toDomainResult { dto ->
                    dto.takeIf { it.accessToken.isNotBlank() }?.let { it to it.toDomain() }
                }
            when (verified) {
                is DomainResult.Success -> {
                    val (dto, domain) = verified.data
                    if (domain == null) {
                        DomainResult.TechnicalError
                    } else {
                        try {
                            session.startSession(dto.accessToken, dto.refreshToken?.takeIf { it.isNotBlank() })
                            DomainResult.Success(domain)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            DomainResult.TechnicalError // tokens could not be stored: not signed in
                        }
                    }
                }
                is DomainResult.Error -> verified
                DomainResult.NetworkError -> DomainResult.NetworkError
                DomainResult.Empty, DomainResult.TechnicalError -> DomainResult.TechnicalError
            }
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun logout(): DomainResult<Unit> {
        var outcome: DomainResult<Unit> = DomainResult.TechnicalError
        try {
            // First, while the Bearer still exists; a 401 here refreshes like any call.
            outcome = api.logout().toDomainResult { Unit }
        } finally {
            // Always, also when the call threw or the caller was cancelled.
            withContext(NonCancellable) {
                try {
                    session.endSession()
                } catch (failure: Exception) {
                    // The flag is already off; a storage failure must not turn a sign-out into an error.
                }
            }
        }
        return outcome
    }

    override suspend fun me(): DomainResult<AuthUser> =
        api.getProfile().toDomainResult { it.toDomain() }.emptyIsTechnical()

    override suspend fun updateProfile(
        fullName: String,
        locale: UserLocale?,
    ): DomainResult<AuthUser> =
        api.updateProfile(UpdateProfileDto(fullName = fullName, locale = locale?.wire))
            .toDomainResult { it.toDomain() }
            .emptyIsTechnical()

    private suspend fun <T> withDeviceToken(block: suspend () -> DomainResult<T>): DomainResult<T> =
        when (val device = devices.ensureRegistered()) {
            is DomainResult.Success, DomainResult.Empty -> block()
            is DomainResult.Error -> device
            DomainResult.NetworkError -> DomainResult.NetworkError
            DomainResult.TechnicalError -> DomainResult.TechnicalError
        }

    private fun <T> DomainResult<T>.emptyIsTechnical(): DomainResult<T> =
        if (this is DomainResult.Empty) DomainResult.TechnicalError else this
}
