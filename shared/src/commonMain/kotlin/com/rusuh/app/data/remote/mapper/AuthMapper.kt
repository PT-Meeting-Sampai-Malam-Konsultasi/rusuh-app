package com.rusuh.app.data.remote.mapper

import com.rusuh.app.data.clock.parseStringDateMillis
import com.rusuh.app.data.remote.dto.OtpChallengeDto
import com.rusuh.app.data.remote.dto.SessionDto
import com.rusuh.app.data.remote.dto.UserDto
import com.rusuh.app.domain.model.AuthSession
import com.rusuh.app.domain.model.AuthUser
import com.rusuh.app.domain.model.OtpChallenge
import com.rusuh.app.domain.model.UserLocale

/** Null for an answer without a usable challenge id (a client-side problem, not an empty result). */
internal fun OtpChallengeDto.toDomain(): OtpChallenge? =
    challengeId.takeIf { it.isNotBlank() }?.let {
        OtpChallenge(
            challengeId = it,
            expiresAtMillis = expiresAt?.let(::parseStringDateMillis),
            resendAfterSeconds = resendAfterSeconds,
        )
    }

/** Null for a user without an id. A blank name counts as no name. */
internal fun UserDto.toDomain(): AuthUser? =
    userId.takeIf { it.isNotBlank() }?.let {
        AuthUser(
            userId = it,
            email = email,
            fullName = fullName?.takeIf { name -> name.isNotBlank() },
            locale = UserLocale.fromWire(locale),
        )
    }

/** The tokens are NOT part of the domain model; the repository stores them from the DTO. */
internal fun SessionDto.toDomain(): AuthSession? = user.toDomain()?.let { AuthSession(it, isNewAccount, expiresIn) }
