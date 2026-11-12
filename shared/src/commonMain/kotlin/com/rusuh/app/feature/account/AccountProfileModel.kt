package com.rusuh.app.feature.account

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

/**
 * What the signed-in Account profile shows (from `GET /me`). Read-only: the contract has no
 * profile update, and no NIK is stored in this POC.
 */
@Immutable
data class AccountProfileUi(
    val name: String,
    val email: String,
    val emailVerified: Boolean = true,
)

/**
 * Callbacks of the profile screen.
 *
 * [onChangeEmail] is optional: the "Ganti email" link is only shown when it is non-null
 * (the `/me/email-change` flow is out of scope for this POC).
 * [onDeleteAccount] has no backend endpoint yet, so the default is inert.
 */
@Stable
class AccountProfileActions(
    val onSignOut: () -> Unit,
    val onChangeEmail: (() -> Unit)? = null,
    val onDeleteAccount: () -> Unit = {},
)
