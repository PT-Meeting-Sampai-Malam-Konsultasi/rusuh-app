package com.rusuh.app.feature.tickets

import com.rusuh.app.mvi.MviEffect
import com.rusuh.app.mvi.MviIntent
import com.rusuh.app.mvi.MviState

/** MVI state for the Tiket tab; [ui] is exactly what [TicketsScreen] renders. */
data class TicketsState(
    val ui: TicketsUiState = TicketsUiState.Loading,
) : MviState

/** User intents for the Tiket tab. */
sealed interface TicketsIntent : MviIntent {
    /** Initial load of the user's tickets. */
    data object Load : TicketsIntent

    /** Reload after a failed load. */
    data object Retry : TicketsIntent

    /** The user opened the e-ticket for order [orderId]. */
    data class ViewETicket(val orderId: String) : TicketsIntent

    /** The signed-out user tapped sign in. */
    data object SignIn : TicketsIntent

    /** The user tapped browse events (empty / signed-out CTA). */
    data object BrowseEvents : TicketsIntent
}

/** One-time side effects for the Tiket tab. */
sealed interface TicketsEffect : MviEffect {
    /** Open the e-ticket page for order [orderId]. */
    data class OpenETicket(val orderId: String) : TicketsEffect

    /** Open the sign-in flow. */
    data object OpenSignIn : TicketsEffect

    /** Switch to the Event tab. */
    data object GoToEvents : TicketsEffect
}
