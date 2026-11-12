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
    val emailVerified: Boolean,
)

/**
 * Callbacks of the profile screen.
 *
 * [onChangeEmail] is optional: the "Ganti email" link is only shown when it is non-null
 * (the `/me/email-change` flow is out of scope for this POC).
 * [onDeleteAccount] is optional: there is no backend endpoint yet, so with null (default) the
 * "Hapus akun" text looks the same but is not clickable and TalkBack reads it as disabled.
 */
@Stable
class AccountProfileActions(
    val onSignOut: () -> Unit,
    val onChangeEmail: (() -> Unit)? = null,
    val onDeleteAccount: (() -> Unit)? = null,
)
