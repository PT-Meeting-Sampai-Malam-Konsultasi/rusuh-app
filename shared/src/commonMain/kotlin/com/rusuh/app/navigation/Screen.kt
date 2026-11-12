package com.rusuh.app.navigation

import com.rusuh.app.ui.components.RusuhTab

/** Defines app screens with their navigation routes. */
sealed class Screen(val route: String) {
    data object Splash : Screen("splash")

    // --- Top-level tab destinations (each hosts the shared RusuhBottomBar). ---

    /** Event tab — the Home screen. */
    data object Home : Screen("home")

    /** Antrian tab — the My Queues screen (FR-APP-01-M2). */
    data object Queues : Screen("queues")

    /** Tiket tab — stub destination until the Tickets screen is built. */
    data object Tickets : Screen("tickets")

    /** Akun tab: the compact login while signed out (AUTH-00-M5), the account page once signed in. */
    data object Account : Screen("account")

    // --- Pushed destinations (NO bottom bar). ---

    /**
     * Waiting Room for a single event, opened from a My Queues row.
     *
     * @property route the route pattern with an `eventId` path argument.
     */
    data object WaitingRoom : Screen("waiting-room/{eventId}") {
        const val ARG_EVENT_ID = "eventId"

        /** Build the concrete route for [eventId]. */
        fun routeFor(eventId: String): String = "waiting-room/$eventId"
    }

    /**
     * Detail Ticket page for a single event: pick categories, enter attendees, continue to pay.
     *
     * @property route the route pattern with an `eventId` path argument.
     */
    data object Detail : Screen("detail/{eventId}") {
        const val ARG_EVENT_ID = "eventId"

        /** Build the concrete route for [eventId]. */
        fun routeFor(eventId: String): String = "detail/$eventId"
    }

    /**
     * Full-screen login (AUTH-00-M4), pushed from the waiting room (entry point: #103).
     * The optional `eventId` query argument names the event the user continues to afterwards.
     */
    data object SignIn : Screen("sign-in?eventId={eventId}") {
        const val ARG_EVENT_ID = "eventId"

        /** Build the concrete route, with or without the event the user continues to. */
        fun routeFor(eventId: String? = null): String = if (eventId == null) "sign-in" else "sign-in?eventId=$eventId"
    }
}

/**
 * The top-level route a [RusuhTab] navigates to. Pure mapping — unit-tested in
 * commonTest so the bar<->route wiring is verified without a nav host.
 */
fun RusuhTab.destinationRoute(): String =
    when (this) {
        RusuhTab.Event -> Screen.Home.route
        RusuhTab.Antrian -> Screen.Queues.route
        RusuhTab.Tiket -> Screen.Tickets.route
        RusuhTab.Akun -> Screen.Account.route
    }
