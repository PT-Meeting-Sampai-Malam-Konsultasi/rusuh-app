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
     * the server; the consent version is added by the implementation. The answer never reveals whether the email has an
     * account.
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
     * Signs out: the app is signed out in memory at once (the local session is cleared first, then the server
     * revocation is tried with a bounded wait, so offline logout is instant). Skipped server call when not signed in.
     * The result is the server call's, for diagnostics, except [DomainResult.TechnicalError] when clearing storage
     * failed: a refresh token may then remain on disk, so offer a retry.
     */
    suspend fun logout(): DomainResult<Unit>

    /** The signed-in account (prefill and the new-account name check). */
    suspend fun me(): DomainResult<AuthUser>
}
