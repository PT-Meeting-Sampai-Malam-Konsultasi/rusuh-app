package com.rusuh.app.feature.waitingroom

import com.rusuh.app.mvi.MviEffect
import com.rusuh.app.mvi.MviIntent
import com.rusuh.app.mvi.MviState

/**
 * State of the Waiting Room.
 *
 * @property ui what the screen renders (the six phases plus loading/empty).
 * @property loadFailed true when the queue could not be loaded. The error copy is a
 * string resource, so the host composable turns this into [WaitingRoomUiState.Error]
 * (see [WaitingRoomState.toScreenState]); the ViewModel stays free of UI text.
 */
data class WaitingRoomState(
    val ui: WaitingRoomUiState = WaitingRoomUiState.Loading,
    val loadFailed: Boolean = false,
) : MviState

/** User intents for the Waiting Room; mirrors [WaitingRoomAction] plus [Start]. */
sealed interface WaitingRoomIntent : MviIntent {
    /** Begin observing the queue place (idempotent; restarts observation). */
    data object Start : WaitingRoomIntent

    data object Back : WaitingRoomIntent

    data object SignIn : WaitingRoomIntent

    /** "Choose tickets now" on the your-turn phase. */
    data object ChooseTicketsNow : WaitingRoomIntent

    data object QueueAgain : WaitingRoomIntent

    data object LeaveQueue : WaitingRoomIntent

    /** "See other events" on the sold-out phase. */
    data object BrowseOtherEvents : WaitingRoomIntent

    data object NotifyMe : WaitingRoomIntent
}

/** One-time side effects for the Waiting Room. */
sealed interface WaitingRoomEffect : MviEffect {
    data object NavigateBack : WaitingRoomEffect

    /** Open the event detail / ticket selection for [eventId]. */
    data class NavigateToDetail(val eventId: String) : WaitingRoomEffect

    /** Go to the Events tab. */
    data object NavigateToEvents : WaitingRoomEffect
}

/** Maps a screen [WaitingRoomAction] onto the matching [WaitingRoomIntent]. */
fun WaitingRoomAction.toIntent(): WaitingRoomIntent =
    when (this) {
        WaitingRoomAction.Back -> WaitingRoomIntent.Back
        WaitingRoomAction.SignIn -> WaitingRoomIntent.SignIn
        WaitingRoomAction.EnterNow -> WaitingRoomIntent.ChooseTicketsNow
        WaitingRoomAction.QueueAgain -> WaitingRoomIntent.QueueAgain
        WaitingRoomAction.LeaveQueue -> WaitingRoomIntent.LeaveQueue
        WaitingRoomAction.SeeOtherEvents -> WaitingRoomIntent.BrowseOtherEvents
        WaitingRoomAction.NotifyMe -> WaitingRoomIntent.NotifyMe
    }
