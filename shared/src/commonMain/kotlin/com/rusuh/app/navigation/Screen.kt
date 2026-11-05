package com.rusuh.app.navigation

/** Defines app screens with their navigation routes. */
sealed class Screen(val route: String) {
    data object Splash : Screen("splash")

    data object Home : Screen("home")
}
