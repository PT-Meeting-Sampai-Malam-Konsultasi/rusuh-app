package com.rusuh.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.navigation.compose.rememberNavController
import com.rusuh.app.navigation.RusuhNavGraph
import com.rusuh.app.ui.theme.RusuhTheme
import org.koin.compose.koinInject

/**
 * App entry composable: applies the theme and hosts the navigation graph.
 *
 * The nav graph starts on the Home (Event) tab and drives the four top-level
 * tab destinations through the shared bottom bar (see [RusuhNavGraph]).
 */
@Composable
fun App() {
    val state: AppLanguageState = koinInject()
    val language by state.language.collectAsState()
    // Apply the platform locale SYNCHRONOUSLY before rendering.
    // This ensures Locale.current (used by CMP Resources) is correct when
    // stringResource/pluralStringResource are resolved during composition.
    applyPlatformLanguage(language.tag)
    CompositionLocalProvider(LocalAppLanguage provides language) {
        ProvideAppLanguageResources(language) {
            key(language) {
                RusuhTheme {
                    val navController = rememberNavController()
                    RusuhNavGraph(navController = navController)
                }
            }
        }
    }
}
