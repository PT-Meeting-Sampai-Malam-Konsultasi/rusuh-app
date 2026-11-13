package com.rusuh.app.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.rusuh.app.feature.initializeComposeResourceContext
import com.rusuh.app.feature.startScreenTestKoin
import com.rusuh.app.ui.theme.RusuhTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SignInScreenTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun initializeTestEnvironment() {
        initializeComposeResourceContext()
        startScreenTestKoin()
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test fun emailStepShowsTitleAndDisabledSendButton() {
        compose.setContent {
            RusuhTheme {
                SignInContent(SignInUiState.Email(), SignInActions(), SignInVariant.Compact)
            }
        }
        compose.onNodeWithText("Sign in without a password.").assertIsDisplayed()
        // Verify button enablement logic: empty email means disabled, valid email means enabled
        assert(!SignInUiState.Email().isPrimaryEnabled()) { "Empty email should disable the primary button" }
        assert(SignInUiState.Email("guest@example.com").isPrimaryEnabled()) {
            "Valid email should enable the primary button"
        }
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
            .assertIsDisplayed()
    }

    @Test fun wrongCodeShowsAttemptsAndInlineError() {
        val attemptsLeft = 3
        compose.setContent {
            RusuhTheme {
                SignInContent(
                    SignInUiState.WrongCode("guest@example.com", "111111", attemptsLeft = attemptsLeft),
                    SignInActions(),
                    SignInVariant.Compact,
                )
            }
        }
        compose.waitForIdle()
        // Assert the code step heading is still shown (WrongCode shares the CodeStep UI)
        compose.onNodeWithText("Check your email.").assertIsDisplayed()
        // Assert the inline error message with attempts count is displayed.
        // The message reads "Wrong code. 3 attempts left, then wait 15 minutes to request a new code."
        // ErrorLine uses clearAndSetSemantics with contentDescription, so we search by contentDescription.
        // The error line uses a polite live region for accessibility.
        compose.onNodeWithContentDescription("Wrong code", substring = true).fetchSemanticsNode()
        // Also verify the attempts count appears (this would fail if attempts weren't rendered)
        compose.onNodeWithContentDescription("$attemptsLeft attempt", substring = true).fetchSemanticsNode()
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
        compose.onNodeWithText("Check your email.").assertIsDisplayed()
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
        compose.onNodeWithText("Code expired.").assertIsDisplayed()
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
        compose.onNodeWithText("One more step.").assertIsDisplayed()
    }
}
