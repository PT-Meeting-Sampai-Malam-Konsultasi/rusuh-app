@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.auth

import androidx.compose.runtime.Composable
import com.rusuh.app.ui.theme.RusuhTheme

// Preview-style sample composables (no @Preview annotation: commonMain has no
// uiTooling-preview dependency, same convention as MyQueuesScreenPreviews).
// Placeholder data only.

private const val SAMPLE_EMAIL = "nama@email.com"

@Composable
internal fun SignInEmailPreview() {
    RusuhTheme { SignInSheet(state = SignInUiState.Email(SAMPLE_EMAIL)) }
}

@Composable
internal fun SignInCodePreview() {
    RusuhTheme {
        SignInSheet(state = SignInUiState.Code(email = SAMPLE_EMAIL, code = "123", resendInSeconds = 42))
    }
}

@Composable
internal fun SignInWrongCodePreview() {
    RusuhTheme {
        SignInSheet(state = SignInUiState.WrongCode(email = SAMPLE_EMAIL, code = "654321", attemptsLeft = 4))
    }
}

@Composable
internal fun SignInCodeExpiredPreview() {
    RusuhTheme { SignInSheet(state = SignInUiState.CodeExpired(email = SAMPLE_EMAIL)) }
}

@Composable
internal fun SignInNewAccountPreview() {
    RusuhTheme {
        SignInSheet(
            state = SignInUiState.NewAccount(email = SAMPLE_EMAIL, fullName = "Sari Wulandari", consentAccepted = true),
        )
    }
}
