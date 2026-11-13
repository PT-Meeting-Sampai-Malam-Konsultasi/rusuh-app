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
 * Error types for the Home screen. The UI resolves these to localized strings via
 * [stringResource]; ViewModels never produce user-visible text.
 */
sealed interface HomeError {
    /** Generic load failure (network, timeout, unexpected error). */
    data object LoadFailed : HomeError
}

/**
 * UI state for the Home screen.
 *
 * @property isLoading true while events are being (re)loaded.
 * @property events the currently loaded events; empty until loaded.
 * @property error typed error, or null when there is no error. The screen resolves it to a localized string.
 */
data class HomeState(
    val isLoading: Boolean = false,
    val events: List<HomeEventItem> = emptyList(),
    val error: HomeError? = null,
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

/**
 * True when a tap on an event with this status opens its queue: the waiting room is open or sales are running, also
 * almost gone and temporarily full (a place is kept while stock frees up). Coming soon, sold out and ended cards do
 * nothing.
 */
fun EventStatus.canQueue(): Boolean =
    when (this) {
        EventStatus.WaitingRoomOpen, EventStatus.OnSale, EventStatus.AlmostGone, EventStatus.TemporarilyFull -> true
        is EventStatus.ComingSoon, is EventStatus.SoldOut, is EventStatus.Ended -> false
    }

/** One-time side effects for the Home screen. */
sealed interface HomeEffect : MviEffect {
    /** Open the Waiting Room of the event with the given [id]; the Waiting Room joins the queue. */
    data class NavigateToQueue(val id: String) : HomeEffect

    /** Show a transient error; the UI resolves [error] to a localized string. */
    data class ShowError(val error: HomeError) : HomeEffect
}
