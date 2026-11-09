package com.rusuh.app.feature.home

import com.rusuh.app.mvi.MviEffect
import com.rusuh.app.mvi.MviIntent
import com.rusuh.app.mvi.MviState
import com.rusuh.app.ui.components.EventFormat
import com.rusuh.app.ui.components.EventPosterTone
import com.rusuh.app.ui.components.EventStatus
import com.rusuh.app.ui.components.TimeZoneLabel

/**
 * Event model for the Home list.
 *
 * Carries the display fields the Figma event card needs. Every field except [id]
 * and [title] has a neutral default so a minimal event (id + title) still maps to a
 * valid card; a real data source (M4) fills the rest. Display strings are expected
 * to be pre-formatted by the data layer, matching [com.rusuh.app.ui.components.EventCardData].
 *
 * shortcut: reuses the UI card types for status/format/timezone until M4 lands a
 * shared domain event type; replace those with domain types then.
 *
 * @property priceFrom starting amount without the "From" prefix (e.g. "Rp 325.000").
 */
data class HomeEventItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val format: EventFormat = EventFormat.UNKNOWN,
    val venue: String = "",
    val city: String = "",
    val dateTime: String = "",
    val timeZone: TimeZoneLabel = TimeZoneLabel.WIB,
    val status: EventStatus = EventStatus.OnSale,
    val priceFrom: String? = null,
    val posterTone: EventPosterTone = EventPosterTone.Blue,
)

/** Interface languages offered by the header toggle. The codes are shown verbatim ("ID" | "EN"). */
enum class HomeLanguage(val code: String) {
    ID("ID"),
    EN("EN"),
}

/**
 * UI state for the Home screen.
 *
 * @property isLoading true while events are being (re)loaded.
 * @property events the currently loaded events; empty until loaded.
 * @property error a user-facing error message, or null when there is no error.
 */
data class HomeState(
    val isLoading: Boolean = false,
    val events: List<HomeEventItem> = emptyList(),
    val error: String? = null,
) : MviState

/** User intents for the Home screen. */
sealed interface HomeIntent : MviIntent {
    /** Initial load of the event list. */
    data object LoadEvents : HomeIntent

    /** Pull-to-refresh / explicit reload of the event list. */
    data object RefreshEvents : HomeIntent

    /** The user tapped an event with the given [id]. */
    data class EventClicked(val id: String) : HomeIntent
}

/** One-time side effects for the Home screen. */
sealed interface HomeEffect : MviEffect {
    /** Navigate to the detail screen for the event with the given [id]. */
    data class NavigateToEvent(val id: String) : HomeEffect

    /** Show a transient error [message] (snackbar/toast). */
    data class ShowError(val message: String) : HomeEffect
}
