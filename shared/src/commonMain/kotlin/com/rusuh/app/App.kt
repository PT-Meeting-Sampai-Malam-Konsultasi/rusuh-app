package com.rusuh.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.rusuh.app.navigation.RusuhNavGraph
import com.rusuh.app.ui.theme.RusuhTheme

/**
 * App entry composable: applies the theme and hosts the navigation graph.
 *
 * The nav graph starts on the Home (Event) tab and drives the four top-level
 * tab destinations through the shared bottom bar (see [RusuhNavGraph]).
 */
@Composable
fun App() {
    RusuhTheme {
        val navController = rememberNavController()
        RusuhNavGraph(navController = navController)
    }
}
