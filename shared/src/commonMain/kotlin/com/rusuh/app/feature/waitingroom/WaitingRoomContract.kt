package com.rusuh.app.feature.waitingroom

import com.rusuh.app.mvi.MviEffect
import com.rusuh.app.mvi.MviIntent
import com.rusuh.app.mvi.MviState

/**
 * State of the Waiting Room.
 *
 * @property ui what the screen renders (the six phases plus loading/empty/error). A load
 * failure is [WaitingRoomUiState.Error] with a blank message: the error copy is a string
 * resource, so the host fills it in (see [WaitingRoomState.toScreenState]) and the
 * ViewModel stays free of UI text.
 */
data class WaitingRoomState(
    val ui: WaitingRoomUiState = WaitingRoomUiState.Loading,
) : MviState {
    /** True while the phase shows a live countdown the host must tick (pre-queue, your turn). */
    val hasCountdown: Boolean
        get() {
            val phase = (ui as? WaitingRoomUiState.Active)?.phase
            return phase is WaitingRoomPhase.PreQueue || phase is WaitingRoomPhase.YourTurn
        }
}

/** User intents for the Waiting Room; mirrors [WaitingRoomAction] plus [Start] and [Tick]. */
sealed interface WaitingRoomIntent : MviIntent {
    /** (Re)start observing the queue place from a clean Loading state. */
    data object Start : WaitingRoomIntent

    /** Recompute countdowns from the last snapshot; sent about once a second by the host. */
    data object Tick : WaitingRoomIntent

    data object ScreenActive : WaitingRoomIntent

    data object ScreenInactive : WaitingRoomIntent

    data object Back : WaitingRoomIntent

    /** "Choose tickets now" on the your-turn phase. */
    data object ChooseTicketsNow : WaitingRoomIntent

    data object QueueAgain : WaitingRoomIntent

    data object LeaveQueue : WaitingRoomIntent

    /** "See other events" on the sold-out phase. */
    data object BrowseOtherEvents : WaitingRoomIntent

    data object NotifyMe : WaitingRoomIntent

    /** Resume observation after a failure; keeps the last good place on screen if there is one. */
    data object Retry : WaitingRoomIntent
}

/** One-time side effects for the Waiting Room. */
sealed interface WaitingRoomEffect : MviEffect {
    data object NavigateBack : WaitingRoomEffect

    /** Open the event detail / ticket selection for [eventId]. */
    data class NavigateToDetail(val eventId: String) : WaitingRoomEffect

    /** Go to the Events tab. */
    data object NavigateToEvents : WaitingRoomEffect

    /**
     * Defensive re-auth: the session was lost mid-queue (401/SignInRequiredException during polling).
     * Opens sign-in for [eventId]; after re-signing in the user resumes the same place.
     */
    data class ReSignIn(val eventId: String) : WaitingRoomEffect

    /** Updates stopped after a place was already shown; the last place stays on screen. */
    data object ShowRefreshError : WaitingRoomEffect

    data object ShowNoStock : WaitingRoomEffect
}

/** Maps a screen [WaitingRoomAction] onto the matching [WaitingRoomIntent]. */
fun WaitingRoomAction.toIntent(): WaitingRoomIntent =
    when (this) {
        WaitingRoomAction.Back -> WaitingRoomIntent.Back
        WaitingRoomAction.EnterNow -> WaitingRoomIntent.ChooseTicketsNow
        WaitingRoomAction.QueueAgain -> WaitingRoomIntent.QueueAgain
        WaitingRoomAction.LeaveQueue -> WaitingRoomIntent.LeaveQueue
        WaitingRoomAction.SeeOtherEvents -> WaitingRoomIntent.BrowseOtherEvents
        WaitingRoomAction.NotifyMe -> WaitingRoomIntent.NotifyMe
        WaitingRoomAction.Retry -> WaitingRoomIntent.Retry
    }
