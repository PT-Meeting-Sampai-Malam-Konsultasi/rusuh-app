package com.rusuh.app.feature.tickets

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
class TicketsScreenTest {
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

    @Test fun signedOutTabExplainsSignIn() {
        compose.setContent { RusuhTheme { TicketsScreen(state = TicketsUiState.SignedOut) } }
        compose.onNodeWithText("Sign in to see your tickets.").assertIsDisplayed()
        compose.onNodeWithText("Sign in →").assertIsDisplayed()
    }
}
