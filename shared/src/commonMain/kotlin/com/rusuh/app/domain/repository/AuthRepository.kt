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
     * Signs out. The local session is always cleared first (the UI never waits for the network): after this call,
     * and already while it is still running, `SessionManager.isSignedIn` is false, so navigate on that, not on the
     * result. The result only describes the server revoke (bounded to 5 s; with an expired or missing access token one
     * refresh is made to get a token to revoke with): [DomainResult.NetworkError] when it could not be reached, and
     * [DomainResult.TechnicalError] when clearing storage failed (a refresh token may remain on disk: offer a retry).
     * A 401 from the revoke is not an error. [DomainResult.NetworkError] means "not revoked, not worth retrying": the
     * local session is gone and a rotated refresh token may stay valid on the server until it expires. Before the clear
     * it may wait up to 5 s for a hanging cold-start storage read. No server call when not signed in. A [verifyOtp]
     * that completes after a logout signs the user back in (TODO(AUTH-00 #77): generation counter in the state layer).
     */
    suspend fun logout(): DomainResult<Unit>

    /** The signed-in account (prefill and the new-account name check). */
    suspend fun me(): DomainResult<AuthUser>
}
