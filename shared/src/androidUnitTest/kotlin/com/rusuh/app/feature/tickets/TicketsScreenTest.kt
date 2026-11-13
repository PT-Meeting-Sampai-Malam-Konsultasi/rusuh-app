package com.rusuh.app.feature.tickets

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rusuh.app.ui.theme.RusuhTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TicketsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Before fun initializeTestEnvironment() {
        com.rusuh.app.feature.initializeComposeResourceContext()
        com.rusuh.app.feature.startScreenTestKoin()
    }

    @Test fun signedOutTabExplainsSignIn() {
        compose.setContent { RusuhTheme { TicketsScreen(state = TicketsUiState.SignedOut) } }
        compose.onNodeWithText("Sign in to see your tickets.").assertIsDisplayed()
        compose.onNodeWithText("Sign in →").assertIsDisplayed()
    }
}
