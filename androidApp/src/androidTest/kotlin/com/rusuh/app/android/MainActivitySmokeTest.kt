package com.rusuh.app.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun guestCanSignInUsingStubCode() {
        compose.onNodeWithText("Akun").performClick()
        compose.onNodeWithText("Sign in without a password.").assertIsDisplayed()
        compose.onNodeWithText("Email").performTextInput("guest@example.com")
        compose.onNodeWithText("Kirim kode").performClick()
        compose.onNodeWithContentDescription("Verification code").performTextInput("123456")
        compose.onNodeWithText("Masuk").performClick()
    }
}
