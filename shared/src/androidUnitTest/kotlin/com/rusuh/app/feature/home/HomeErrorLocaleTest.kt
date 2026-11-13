package com.rusuh.app.feature.home

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
import org.robolectric.annotation.Config

/**
 * Robolectric tests proving the Home error state displays localized text.
 *
 * These tests verify issue #134: error messages must not be hardcoded English
 * but must come from string resources and display in the user's locale.
 */
@RunWith(RobolectricTestRunner::class)
class HomeErrorLocaleTest {
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

    @Test
    @Config(qualifiers = "en")
    fun homeErrorStateShowsEnglishTextInEnglishLocale() {
        compose.setContent {
            RusuhTheme {
                HomeScreen(
                    state = HomeState(error = HomeError.LoadFailed),
                    dispatch = {},
                )
            }
        }

        // The English error title from values/strings.xml
        compose.onNodeWithText("Could not load events").assertIsDisplayed()
        compose.onNodeWithText("Check your connection and try again.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "in")
    fun homeErrorStateShowsIndonesianTextInIndonesianLocale() {
        compose.setContent {
            RusuhTheme {
                HomeScreen(
                    state = HomeState(error = HomeError.LoadFailed),
                    dispatch = {},
                )
            }
        }

        // The Indonesian error title from values-in/strings.xml
        compose.onNodeWithText("Gagal memuat event").assertIsDisplayed()
        compose.onNodeWithText("Periksa koneksi Anda lalu coba lagi.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "id")
    fun homeErrorStateShowsIndonesianTextInIdLocale() {
        // iOS uses "id" locale code for Indonesian
        compose.setContent {
            RusuhTheme {
                HomeScreen(
                    state = HomeState(error = HomeError.LoadFailed),
                    dispatch = {},
                )
            }
        }

        // The Indonesian error title from values-id/strings.xml
        compose.onNodeWithText("Gagal memuat event").assertIsDisplayed()
        compose.onNodeWithText("Periksa koneksi Anda lalu coba lagi.").assertIsDisplayed()
    }
}
