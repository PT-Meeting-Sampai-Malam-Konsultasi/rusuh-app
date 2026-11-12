@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.auth

import androidx.compose.runtime.Composable
import com.rusuh.app.ui.theme.RusuhTheme

// Preview-style sample composables (no @Preview annotation: commonMain has no
// uiTooling-preview dependency, same convention as MyQueuesScreenPreviews).
// Synthetic data only. The full variant is covered here, the Account-tab one in AccountScreenPreviews.

private const val SAMPLE_EMAIL = "nama@email.com"
private val SAMPLE_EVENT = SignInEventContext(name = "Samudra", tag = "Tur Gelombang 2026", openingTime = "10.00 WIB")

@Composable
private fun FullPreview(state: SignInUiState) {
    RusuhTheme { SignInScreen(state = state, event = SAMPLE_EVENT) }
}

@Composable
internal fun SignInFullEmailPreview() = FullPreview(SignInUiState.Email(SAMPLE_EMAIL))

@Composable
internal fun SignInFullCodePreview() =
    FullPreview(SignInUiState.Code(email = SAMPLE_EMAIL, code = "4820", resendInSeconds = 42, validForSeconds = 291))

@Composable
internal fun SignInFullWrongCodePreview() =
    FullPreview(SignInUiState.WrongCode(email = SAMPLE_EMAIL, code = "482091", attemptsLeft = 2, validForSeconds = 291))

@Composable
internal fun SignInFullCodeExpiredPreview() = FullPreview(SignInUiState.CodeExpired(email = SAMPLE_EMAIL))

@Composable
internal fun SignInFullNewAccountPreview() =
    FullPreview(SignInUiState.NewAccount(email = SAMPLE_EMAIL, fullName = "Sari Wulandari"))
