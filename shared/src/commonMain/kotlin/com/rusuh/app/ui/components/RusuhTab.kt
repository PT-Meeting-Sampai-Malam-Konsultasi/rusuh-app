package com.rusuh.app.ui.components

/**
 * The four top-level destinations of the app, shown as the bottom navigation
 * tabs. Order matches the hi-fi tab bar (home.html `.tabbar`): Event, Antrian,
 * Tiket, Akun.
 *
 * Kept rendering-free (an enum with its label + glyph) so the tab->label
 * mapping can be unit-tested in commonTest without a Compose harness and so the
 * navigation layer can switch on it directly.
 *
 * @param label the tab caption shown under the icon (Indonesian, per the hi-fi).
 * @param glyph a single-character stand-in for the tab icon. A text glyph is
 *   used rather than pulling material-icons into commonMain for four icons,
 *   matching the "text chevron" choice in WaitingRoomScreen / DetailScreen.
 */
enum class RusuhTab(val label: String, val glyph: String) {
    /** Events home (the existing Home screen). */
    Event("Event", "\u2302"),

    /** My Queues — the events the user is currently queued for. */
    Antrian("Antrian", "\u2630"),

    /** The user's purchased tickets (screen not built yet). */
    Tiket("Tiket", "\u25AF"),

    /** The user's account (screen not built yet). */
    Akun("Akun", "\u25CB"),
}
