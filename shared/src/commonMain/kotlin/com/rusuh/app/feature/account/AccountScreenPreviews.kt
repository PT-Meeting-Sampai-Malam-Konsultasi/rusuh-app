@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.account

import androidx.compose.runtime.Composable
import com.rusuh.app.feature.auth.SignInUiState
import com.rusuh.app.ui.theme.RusuhTheme

// Preview-style sample composables (no @Preview annotation, see MyQueuesScreenPreviews).
// Synthetic data only. Compact login = Account tab while signed out (AUTH-00-M5 #102).

private const val SAMPLE_EMAIL = "nama@email.com"

@Composable
private fun CompactPreview(state: SignInUiState) {
    RusuhTheme { AccountScreen(isSignedIn = false, signInState = state) }
}

@Composable
internal fun AccountSignInEmailPreview() = CompactPreview(SignInUiState.Email(SAMPLE_EMAIL))

@Composable
internal fun AccountSignInCodePreview() =
    CompactPreview(SignInUiState.Code(email = SAMPLE_EMAIL, code = "4820", resendInSeconds = 42, validForSeconds = 291))

@Composable
internal fun AccountSignInWrongCodePreview() =
    CompactPreview(
        SignInUiState.WrongCode(email = SAMPLE_EMAIL, code = "482091", attemptsLeft = 2, validForSeconds = 291),
    )

@Composable
internal fun AccountSignInCodeExpiredPreview() = CompactPreview(SignInUiState.CodeExpired(email = SAMPLE_EMAIL))

@Composable
internal fun AccountSignInNewAccountPreview() =
    CompactPreview(SignInUiState.NewAccount(email = SAMPLE_EMAIL, fullName = "Sari Wulandari"))

@Composable
internal fun AccountSignedInPreview() {
    RusuhTheme { AccountScreen(isSignedIn = true, signInState = SignInUiState.Email()) }
}
