package com.rusuh.app.navigation

import com.rusuh.app.ui.components.RusuhTab
import io.ktor.http.encodeURLParameter

/** Defines app screens with their navigation routes. */
sealed class Screen(val route: String) {
    data object Splash : Screen("splash")

    /** Analytics consent, shown once at first launch when consent is Unknown. */
    data object Consent : Screen("consent")

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
     * E-ticket page of one order, opened from the Tiket tab. It lives inside the Tiket tab (the bottom bar
     * stays, Tiket selected) and Back returns to the tab.
     *
     * @property route the route pattern with a URL-encoded `orderId` path argument.
     */
    data object ETicket : Screen("eticket/{orderId}") {
        const val ARG_ORDER_ID = "orderId"

        /** Build the concrete route for [orderId], URL-encoded so it stays one path segment. */
        fun routeFor(orderId: String): String = "eticket/${encodePathSegment(orderId)}"
    }

    /**
     * Full-screen login (AUTH-00-M4), pushed from the waiting room (entry point: #103).
     * The optional `eventId` query argument names the event the user continues to afterwards.
     */
    data object SignIn : Screen("sign-in?eventId={eventId}") {
        const val ARG_EVENT_ID = "eventId"

        /** Build the concrete route, with or without the event the user continues to. */
        fun routeFor(eventId: String? = null): String =
            if (eventId == null) "sign-in" else "sign-in?eventId=${eventId.encodeURLParameter()}"
    }

    /**
     * Payment screen showing the order summary and Midtrans Snap handoff.
     * Opened from the Detail screen after booking is held.
     */
    data object Payment : Screen("payment/{bookingId}") {
        const val ARG_BOOKING_ID = "bookingId"

        /** Build the concrete route for [bookingId]. */
        fun routeFor(bookingId: String): String = "payment/${encodePathSegment(bookingId)}"
    }

    /**
     * Payment result screen reached via App Link / Universal Link after returning from Midtrans Snap.
     * NEVER trusts the URL: only takes the bookingId and fetches actual status from the server.
     */
    data object PaymentResult : Screen("payment-result?bookingId={bookingId}") {
        const val ARG_BOOKING_ID = "bookingId"

        /** Build the concrete route for [bookingId]. */
        fun routeFor(bookingId: String): String = "payment-result?bookingId=${bookingId.encodeURLParameter()}"
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

/** Percent-encodes everything but unreserved characters so an id cannot add path segments or a query. */
internal fun encodePathSegment(value: String): String =
    buildString {
        for (byte in value.encodeToByteArray()) {
            val c = byte.toInt() and 0xFF
            val unreserved = c < 0x80 && (c.toChar().isLetterOrDigit() || c.toChar() in "-._~")
            if (unreserved) append(c.toChar()) else append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
        }
    }

private const val HEX = "0123456789ABCDEF"
