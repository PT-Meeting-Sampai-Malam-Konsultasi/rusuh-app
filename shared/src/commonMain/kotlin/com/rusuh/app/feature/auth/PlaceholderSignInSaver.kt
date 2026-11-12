package com.rusuh.app.feature.auth

import androidx.compose.runtime.saveable.Saver

private const val TAG_CLOSED = "closed"
private const val TAG_EMAIL = "email"
private const val TAG_CODE = "code"
private const val TAG_WRONG = "wrong"
private const val TAG_EXPIRED = "expired"
private const val TAG_NEW = "new"

// Layout of the flat saved list (only primitives, so every platform can store it).
private const val IDX_TAG = 0
private const val IDX_EMAIL = 1
private const val IDX_CODE = 2
private const val IDX_NAME = 3
private const val IDX_RESEND = 4
private const val IDX_VALID = 5
private const val IDX_ATTEMPTS_LEFT = 6
private const val IDX_CONSENT = 7
private const val IDX_SIGNED_IN = 8
private const val IDX_ATTEMPTS = 9
private const val IDX_ACCOUNT_NAME = 10
private const val IDX_ACCOUNT_EMAIL = 11

/** Flattens a step (or null, the closed flow) into primitives; inverse of [decodeSignInState]. */
internal fun encodeSignInState(state: SignInUiState?): List<Any> {
    // tag, email, code, name, resend, valid, attemptsLeft, consent
    val fields: MutableList<Any> = mutableListOf(TAG_CLOSED, "", "", "", 0, 0, 0, false)
    when (state) {
        null -> Unit
        is SignInUiState.Email -> {
            fields[IDX_TAG] = TAG_EMAIL
            fields[IDX_EMAIL] = state.email
        }
        is SignInUiState.Code -> {
            fields[IDX_TAG] = TAG_CODE
            fields[IDX_EMAIL] = state.email
            fields[IDX_CODE] = state.code
            fields[IDX_RESEND] = state.resendInSeconds
            fields[IDX_VALID] = state.validForSeconds
        }
        is SignInUiState.WrongCode -> {
            fields[IDX_TAG] = TAG_WRONG
            fields[IDX_EMAIL] = state.email
            fields[IDX_CODE] = state.code
            fields[IDX_RESEND] = state.resendInSeconds
            fields[IDX_VALID] = state.validForSeconds
            fields[IDX_ATTEMPTS_LEFT] = state.attemptsLeft
        }
        is SignInUiState.CodeExpired -> {
            fields[IDX_TAG] = TAG_EXPIRED
            fields[IDX_EMAIL] = state.email
            fields[IDX_CODE] = state.code
        }
        is SignInUiState.NewAccount -> {
            fields[IDX_TAG] = TAG_NEW
            fields[IDX_EMAIL] = state.email
            fields[IDX_NAME] = state.fullName
            fields[IDX_CONSENT] = state.consentAccepted
        }
    }
    return fields
}

/** Rebuilds the step flattened by [encodeSignInState]; an unknown tag reads as the closed flow. */
internal fun decodeSignInState(saved: List<Any>): SignInUiState? {
    val email = saved[IDX_EMAIL] as String
    val code = saved[IDX_CODE] as String
    return when (saved[IDX_TAG]) {
        TAG_EMAIL -> SignInUiState.Email(email)
        TAG_CODE -> SignInUiState.Code(email, code, saved[IDX_RESEND] as Int, saved[IDX_VALID] as Int)
        TAG_WRONG ->
            SignInUiState.WrongCode(
                email,
                code,
                saved[IDX_ATTEMPTS_LEFT] as Int,
                saved[IDX_RESEND] as Int,
                saved[IDX_VALID] as Int,
            )
        TAG_EXPIRED -> SignInUiState.CodeExpired(email, code)
        TAG_NEW -> SignInUiState.NewAccount(email, saved[IDX_NAME] as String, saved[IDX_CONSENT] as Boolean)
        else -> null
    }
}

/** Keeps the placeholder flow across tab switches and process death (demo convenience only). */
internal val PlaceholderSignInSaver: Saver<PlaceholderSignIn, List<Any>> =
    Saver(
        save = { signIn ->
            encodeSignInState(signIn.state) + signIn.isSignedIn + signIn.attemptsLeft +
                signIn.signedInName + signIn.signedInEmail
        },
        restore = { saved ->
            PlaceholderSignIn().also {
                it.restore(
                    restoredState = decodeSignInState(saved),
                    signedIn = saved[IDX_SIGNED_IN] as Boolean,
                    attempts = saved[IDX_ATTEMPTS] as Int,
                    name = saved[IDX_ACCOUNT_NAME] as String,
                    email = saved[IDX_ACCOUNT_EMAIL] as String,
                )
            }
        },
    )
