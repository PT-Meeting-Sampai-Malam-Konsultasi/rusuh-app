package com.rusuh.app.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rusuh.app.ui.theme.RusuhTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Before fun initializeTestEnvironment() {
        com.rusuh.app.feature.initializeComposeResourceContext()
        com.rusuh.app.feature.startScreenTestKoin()
    }

    @Test fun rendersSampleEventList() {
        compose.setContent { RusuhTheme { HomeScreen(HomeState(events = homePreviewEvents().take(2)), {}) } }
        compose.onNodeWithText("All events").assertIsDisplayed()
        compose.onNode(hasContentDescription("Langit Sore", substring = true)).fetchSemanticsNode()
    }
}
