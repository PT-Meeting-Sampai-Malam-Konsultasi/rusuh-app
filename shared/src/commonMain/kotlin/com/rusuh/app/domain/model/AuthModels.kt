package com.rusuh.app.domain.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Account language (`locale` of the contract). */
enum class UserLocale(val wire: String) {
    Indonesian("id"),
    English("en"),
    ;

    companion object {
        fun fromWire(value: String?): UserLocale? = entries.firstOrNull { it.wire == value }
    }
}

/** A code was sent. [expiresAtMillis] is epoch millis (null when the server sent no readable time). */
data class OtpChallenge(
    val challengeId: String,
    val expiresAtMillis: Long?,
    val resendAfterSeconds: Int,
)

/** The signed-in account. [fullName] is null for an account that has not given a name yet. */
data class AuthUser(
    val userId: String,
    val email: String,
    val fullName: String?,
    val locale: UserLocale?,
) {
    override fun toString(): String = "AuthUser(userId=$userId, <redacted>)"
}

/**
 * Result of a successful code check. The tokens never leave the data layer (they are stored by the session layer).
 * [isNewAccount] is true when this sign-in created the account; with a null `user.fullName` the UI asks for the
 * name (and the consent) and requests a new code with `fullName` (the contract has no profile update).
 */
data class AuthSession(
    val user: AuthUser,
    val isNewAccount: Boolean,
    val accessTokenExpiresInSeconds: Int,
)

/**
 * The auth failures a screen distinguishes, derived from a failed [DomainResult] by [authFailure]. Branch on this,
 * never on message text.
 */
sealed interface AuthFailure {
    /**
     * 400 WRONG_CODE (the repository also maps an unmapped 409 on verify to it). [attemptsLeft] is null when the
     * server did not say.
     */
    data class WrongCode(val attemptsLeft: Int?) : AuthFailure

    /** 410 CHALLENGE_EXPIRED: request a new code. */
    data object ChallengeExpired : AuthFailure

    /** 410 CHALLENGE_LOCKED (five wrong codes): request a new code. */
    data object ChallengeLocked : AuthFailure

    /** 429. [retryAfterSeconds] is the `Retry-After` hint when present. */
    data class RateLimited(val retryAfterSeconds: Int?) : AuthFailure

    /** 400: the input was rejected (invalid email, missing name). */
    data object InvalidInput : AuthFailure

    /** 401: no or an invalid session (for the profile calls). */
    data object Unauthorized : AuthFailure

    /** Offline, timeout or connection failure. */
    data object Network : AuthFailure

    /** Anything else; [httpStatus] is 0 for a client-side problem. */
    data class Unknown(val httpStatus: Int) : AuthFailure
}

/**
 * Classifies a failed result; null for [DomainResult.Success] and [DomainResult.Empty]. The contract code decides
 * first. A code this app does not know (the live backend sends `BAD_REQUEST`, see docs/data-layer.md) falls back to
 * the HTTP status for 400, 401 and 429, and 410 means the challenge is gone (expired), so every unknown-code case
 * still lands on a sensible screen.
 */
fun DomainResult<*>.authFailure(): AuthFailure? =
    when (this) {
        is DomainResult.Success, DomainResult.Empty -> null
        DomainResult.NetworkError -> AuthFailure.Network
        DomainResult.TechnicalError -> AuthFailure.Unknown(0)
        is DomainResult.Error -> classify()
    }

private fun DomainResult.Error.classify(): AuthFailure =
    when {
        errorCode == ErrorCode.WRONG_CODE -> AuthFailure.WrongCode(attemptsLeft())
        errorCode == ErrorCode.CHALLENGE_LOCKED -> AuthFailure.ChallengeLocked
        errorCode == ErrorCode.CHALLENGE_EXPIRED || code == HTTP_GONE -> AuthFailure.ChallengeExpired
        errorCode == ErrorCode.RATE_LIMITED || code == HTTP_TOO_MANY -> AuthFailure.RateLimited(retryAfterSeconds)
        errorCode == ErrorCode.VALIDATION_FAILED || code == HTTP_BAD_REQUEST -> AuthFailure.InvalidInput
        errorCode == ErrorCode.UNAUTHORIZED || code == HTTP_UNAUTHORIZED -> AuthFailure.Unauthorized
        else -> AuthFailure.Unknown(code)
    }

private fun DomainResult.Error.attemptsLeft(): Int? = (extras["attemptsLeft"] as? JsonPrimitive)?.intOrNull

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_GONE = 410
private const val HTTP_TOO_MANY = 429
