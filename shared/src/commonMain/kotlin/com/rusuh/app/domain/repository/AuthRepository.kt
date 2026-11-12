package com.rusuh.app.domain.repository

import com.rusuh.app.domain.model.AuthSession
import com.rusuh.app.domain.model.AuthUser
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.OtpChallenge
import com.rusuh.app.domain.model.UserLocale

/**
 * Email-OTP sign-in and the account profile. Failures are [DomainResult.Error] (read them with `authFailure()`),
 * [DomainResult.NetworkError] or [DomainResult.TechnicalError]. Token refresh is not here: the session layer
 * refreshes on 401 by itself (`SessionRefreshCoordinator`); whether a session exists is `SessionManager.isSignedIn`.
 */
interface AuthRepository {
    /**
     * Sends a code to [email]. [fullName] is needed only when the email has no account yet; [locale] defaults on
     * the server (the consent version is not sent yet, see `RequestOtpDto`). The answer never reveals whether the
     * email has an account.
     */
    suspend fun requestOtp(
        email: String,
        fullName: String? = null,
        locale: UserLocale? = null,
    ): DomainResult<OtpChallenge>

    /**
     * Checks the 6-digit [code]. On success the tokens are stored and the session is signed in before this returns.
     * Wrong code: WrongCode with attempts left; expired or locked: request a new code.
     */
    suspend fun verifyOtp(
        challengeId: String,
        code: String,
    ): DomainResult<AuthSession>

    /**
     * Signs out. The server revocation is best effort; the local session (tokens, signed-in state) is ALWAYS cleared,
     * also when the call fails, is cancelled, or storage misbehaves. The returned result is the server call's, for
     * diagnostics: navigate to signed-out regardless.
     */
    suspend fun logout(): DomainResult<Unit>

    /** The signed-in account (prefill and the new-account name check). */
    suspend fun me(): DomainResult<AuthUser>

    /** Sets the display name (and optionally the language) of the signed-in account. */
    suspend fun updateProfile(
        fullName: String,
        locale: UserLocale? = null,
    ): DomainResult<AuthUser>
}
