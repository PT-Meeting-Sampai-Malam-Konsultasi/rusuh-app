package com.rusuh.app

import com.rusuh.app.data.network.AppLanguage
import com.rusuh.app.data.storage.FakeAppPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguageStateTest {
    @Test
    fun selectionIsPersistedAndRestored() =
        runTest {
            val preferences = FakeAppPreferences()
            val state = AppLanguageState { preferences }
            state.select(AppLanguage.English)
            delay(100)
            assertEquals("en", preferences.entries["app_language"])

            val restored = AppLanguageState { preferences }
            delay(100)
            assertEquals(AppLanguage.English, restored.language.value)
        }
}
