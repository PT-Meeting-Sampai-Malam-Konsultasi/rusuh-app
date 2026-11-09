package com.rusuh.app.ui.components

import org.jetbrains.compose.resources.StringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.tab_account
import rusuh_app.shared.generated.resources.tab_event
import rusuh_app.shared.generated.resources.tab_queues
import rusuh_app.shared.generated.resources.tab_tickets

/**
 * The four top-level destinations of the app, shown as the bottom navigation
 * tabs. Order matches the hi-fi tab bar (home.html `.tabbar`): Event, Antrian,
 * Tiket, Akun.
 *
 * Kept rendering-free (an enum with its label + glyph) so the tab->label
 * mapping can be unit-tested in commonTest without a Compose harness and so the
 * navigation layer can switch on it directly.
 *
 * @param label the localized tab caption resource shown under the icon.
 * @param glyph a single-character stand-in for the tab icon. A text glyph is
 *   used rather than pulling material-icons into commonMain for four icons,
 *   matching the "text chevron" choice in WaitingRoomScreen / DetailScreen.
 */
enum class RusuhTab(val label: StringResource, val glyph: String) {
    /** Events home (the existing Home screen). */
    Event(Res.string.tab_event, "\u2302"),

    /** My Queues — the events the user is currently queued for. */
    Antrian(Res.string.tab_queues, "\u2630"),

    /** The user's purchased tickets (screen not built yet). */
    Tiket(Res.string.tab_tickets, "\u25AF"),

    /** The user's account (screen not built yet). */
    Akun(Res.string.tab_account, "\u25CB"),
}
