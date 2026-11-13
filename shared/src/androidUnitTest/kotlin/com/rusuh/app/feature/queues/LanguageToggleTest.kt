package com.rusuh.app.feature.queues

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.rusuh.app.feature.initializeComposeResourceContext
import com.rusuh.app.feature.startScreenTestKoin
import com.rusuh.app.ui.theme.RusuhTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LanguageToggleTest {
    @get:Rule val compose = createComposeRule()

    @Before fun initializeTestEnvironment() {
        initializeComposeResourceContext()
        startScreenTestKoin()
    }

    @Test fun languageGroupAnnouncesItsSelectedSegment() {
        compose.setContent {
            RusuhTheme { LanguageToggle(selected = AppLanguageOption.English) }
        }
        compose.onNodeWithContentDescription("Language").fetchSemanticsNode()
        compose.onNodeWithText("EN").assertIsSelected()
    }
}
