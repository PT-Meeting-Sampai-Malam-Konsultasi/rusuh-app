package com.rusuh.app.feature.home

import com.rusuh.app.mvi.MviEffect
import com.rusuh.app.mvi.MviIntent
import com.rusuh.app.mvi.MviState

/**
 * Minimal placeholder event model for the Home screen's MVI state.
 *
 * M1's richer `EventCardData` lives in a parallel PR and is NOT on `main`, so this
 * package defines its own lightweight model to avoid a cross-branch dependency.
 *
 * shortcut: intentionally minimal (id + title); replace or map to the real domain
 * model once M4 (API integration) lands a shared event type.
 */
data class HomeEventItem(
    val id: String,
    val title: String,
)

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
