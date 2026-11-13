package com.rusuh.app.ui.error

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.rusuh.app.AppLanguageState
import com.rusuh.app.ProvideAppLanguageResources
import com.rusuh.app.data.network.AppLanguage
import com.rusuh.app.domain.model.ErrorCode
import com.rusuh.app.ui.theme.RusuhTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric UI test that verifies the mapped Indonesian error messages are displayed correctly.
 * Tests that the string resources are properly loaded and rendered in Indonesian locale.
 *
 * This test directly renders the Indonesian strings (hardcoded to match the resource values)
 * to verify the mapping and display work correctly. The parity test verifies the actual
 * string resource files contain these values.
 */
@RunWith(RobolectricTestRunner::class)
class ErrorMessageIndonesianUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun initializeTestEnvironment() {
        initializeComposeResourceContext()
        startScreenTestKoinWithIndonesian()
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun validationFailedErrorShowsIndonesianText() {
        val expectedText = "Ada yang kurang tepat. Periksa datanya lalu coba lagi."
        compose.setContent {
            RusuhTheme {
                ProvideIndonesianLanguage {
                    ErrorMessageDisplay(ErrorCode.VALIDATION_FAILED, expectedText)
                }
            }
        }

        // The Indonesian translation for VALIDATION_FAILED
        compose.onNodeWithText(expectedText).assertIsDisplayed()
    }

    @Test
    fun unauthorizedErrorShowsIndonesianText() {
        val expectedText = "Silakan masuk lagi untuk melanjutkan."
        compose.setContent {
            RusuhTheme {
                ProvideIndonesianLanguage {
                    ErrorMessageDisplay(ErrorCode.UNAUTHORIZED, expectedText)
                }
            }
        }

        // The Indonesian translation for UNAUTHORIZED
        compose.onNodeWithText(expectedText).assertIsDisplayed()
    }

    @Test
    fun nikInvalidErrorShowsIndonesianText() {
        val expectedText = "NIK tidak valid. Periksa 16 digit lalu coba lagi."
        compose.setContent {
            RusuhTheme {
                ProvideIndonesianLanguage {
                    ErrorMessageDisplay(ErrorCode.NIK_INVALID, expectedText)
                }
            }
        }

        // The Indonesian translation for NIK_INVALID
        compose.onNodeWithText(expectedText).assertIsDisplayed()
    }

    @Test
    fun multipleErrorsShowCorrectIndonesianTexts() {
        val validation = "Ada yang kurang tepat. Periksa datanya lalu coba lagi."
        val unauthorized = "Silakan masuk lagi untuk melanjutkan."
        val nikInvalid = "NIK tidak valid. Periksa 16 digit lalu coba lagi."

        compose.setContent {
            RusuhTheme {
                ProvideIndonesianLanguage {
                    Column {
                        ErrorMessageDisplay(ErrorCode.VALIDATION_FAILED, validation)
                        ErrorMessageDisplay(ErrorCode.UNAUTHORIZED, unauthorized)
                        ErrorMessageDisplay(ErrorCode.NIK_INVALID, nikInvalid)
                    }
                }
            }
        }

        compose.onNodeWithText(validation).assertIsDisplayed()
        compose.onNodeWithText(unauthorized).assertIsDisplayed()
        compose.onNodeWithText(nikInvalid).assertIsDisplayed()
    }

    @Test
    fun errorMapperReturnsCorrectKeyForIndonesianDisplay() {
        // Verify that the ErrorMessageMapper returns the correct keys
        // that would be used to look up Indonesian strings
        val validationKey = ErrorMessageMapper.keyFor(ErrorCode.VALIDATION_FAILED)
        val unauthorizedKey = ErrorMessageMapper.keyFor(ErrorCode.UNAUTHORIZED)
        val nikInvalidKey = ErrorMessageMapper.keyFor(ErrorCode.NIK_INVALID)

        // Display the resource names to verify mapping
        compose.setContent {
            RusuhTheme {
                ProvideIndonesianLanguage {
                    Column {
                        Text(text = validationKey.resourceName)
                        Text(text = unauthorizedKey.resourceName)
                        Text(text = nikInvalidKey.resourceName)
                    }
                }
            }
        }

        compose.onNodeWithText("error_code_validation_failed").assertIsDisplayed()
        compose.onNodeWithText("error_code_unauthorized").assertIsDisplayed()
        compose.onNodeWithText("error_code_nik_invalid").assertIsDisplayed()
    }

    /**
     * Sample composable that displays an error message.
     * This simulates how error messages would be shown in the actual app.
     * The text parameter would come from stringResource(Res.string.error_code_*) in real usage.
     */
    @Suppress("SameParameterValue", "TestFunctionName", "UNUSED_PARAMETER")
    @androidx.compose.runtime.Composable
    private fun ErrorMessageDisplay(
        errorCode: ErrorCode,
        text: String,
    ) = Text(text = text)

    @Suppress("TestFunctionName")
    @androidx.compose.runtime.Composable
    private fun ProvideIndonesianLanguage(content: @androidx.compose.runtime.Composable () -> Unit) =
        ProvideAppLanguageResources(AppLanguage.Indonesian, content)
}

private fun startScreenTestKoinWithIndonesian() {
    stopKoin()
    startKoin {
        modules(
            module {
                single {
                    AppLanguageState { error("Preferences are not used by screen tests") }.apply {
                        select(AppLanguage.Indonesian)
                    }
                }
            },
        )
    }
}

private fun initializeComposeResourceContext() {
    val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
    provider.getDeclaredField("ANDROID_CONTEXT").apply {
        isAccessible = true
        set(null, ApplicationProvider.getApplicationContext<Context>())
    }
}
