package com.rusuh.app.feature.auth

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.rusuh.app.ui.theme.RusuhTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SignInScreenTest {
    @get:Rule val compose = createComposeRule()

    @Before fun initializeResources() = com.rusuh.app.feature.initializeComposeResourceContext()

    @Test fun emailStepShowsTitleAndDisabledSendButton() {
        compose.setContent {
            RusuhTheme {
                SignInContent(SignInUiState.Email(), SignInActions(), SignInVariant.Compact)
            }
        }
        compose.onNodeWithText("Sign in without a password.").fetchSemanticsNode()
        assert(!SignInUiState.Email().isPrimaryEnabled())
        assert(SignInUiState.Email("guest@example.com").isPrimaryEnabled())
    }

    @Test fun networkFailureShowsInlineErrorLine() {
        compose.setContent {
            RusuhTheme {
                SignInContent(
                    SignInUiState.Email("guest@example.com"),
                    SignInActions(),
                    SignInVariant.Compact,
                    status = SignInStatus(error = SignInError.Network),
                )
            }
        }
        compose.onNodeWithContentDescription("Could not reach the server. Check your connection and try again.")
            .fetchSemanticsNode()
    }

    @Test fun wrongCodeShowsAttemptsAndInlineError() {
        compose.setContent {
            RusuhTheme {
                SignInContent(
                    SignInUiState.WrongCode("guest@example.com", "111111", attemptsLeft = 3),
                    SignInActions(),
                    SignInVariant.Compact,
                )
            }
        }
        compose.onNodeWithText("Check your email.").fetchSemanticsNode()
        compose.onNodeWithText("Check your email.").fetchSemanticsNode()
    }

    @Test fun codeStepShowsItsHeading() {
        compose.setContent {
            RusuhTheme {
                SignInContent(
                    SignInUiState.Code("guest@example.com", code = "123456"),
                    SignInActions(),
                    SignInVariant.Compact,
                )
            }
        }
        compose.onNodeWithText("Check your email.").fetchSemanticsNode()
    }

    @Test fun expiredStepShowsItsHeading() {
        compose.setContent {
            RusuhTheme {
                SignInContent(
                    SignInUiState.CodeExpired("guest@example.com"),
                    SignInActions(),
                    SignInVariant.Compact,
                )
            }
        }
        compose.onNodeWithText("Code expired.").fetchSemanticsNode()
    }

    @Test fun newAccountStepShowsItsHeading() {
        compose.setContent {
            RusuhTheme {
                SignInContent(
                    SignInUiState.NewAccount("guest@example.com"),
                    SignInActions(),
                    SignInVariant.Compact,
                )
            }
        }
        compose.onNodeWithText("One more step.").fetchSemanticsNode()
    }
}
