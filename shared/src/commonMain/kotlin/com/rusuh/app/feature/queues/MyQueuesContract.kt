package com.rusuh.app.feature.queues

import com.rusuh.app.mvi.MviEffect
import com.rusuh.app.mvi.MviIntent
import com.rusuh.app.mvi.MviState
import com.rusuh.app.ui.components.QueueListItemUi
import com.rusuh.app.ui.components.RusuhTab

/** Load phase of the My Queues tab. */
enum class MyQueuesPhase { Loading, Ready, Empty, Error }

/** Transient messages the host resolves to localized text (the ViewModel holds no raw text). */
enum class MyQueuesMessage { RefreshFailed, }

/**
 * MVI state for the My Queues tab. Carries no user-facing text; the host resolves
 * error copy from string resources when mapping to [MyQueuesUiState].
 *
 * @property queues valid rows to show; non-empty only in [MyQueuesPhase.Ready].
 */
data class MyQueuesState(
    val phase: MyQueuesPhase = MyQueuesPhase.Loading,
    val queues: List<QueueListItemUi> = emptyList(),
) : MviState

/** User intents for the My Queues tab. */
sealed interface MyQueuesIntent : MviIntent {
    /** Initial load. */
    data object Load : MyQueuesIntent

    /** Reload the list. */
    data object Refresh : MyQueuesIntent

    /** Retry after a load failure. */
    data object Retry : MyQueuesIntent

    /** The user tapped the queue row for [eventId]. */
    data class OpenQueue(val eventId: String) : MyQueuesIntent

    /** The user tapped another bottom-bar [tab]. */
    data class SelectTab(val tab: RusuhTab) : MyQueuesIntent

    /** The user tapped "Browse events" on the empty state. */
    data object BrowseEvents : MyQueuesIntent
}

/** One-time side effects for the My Queues tab. */
sealed interface MyQueuesEffect : MviEffect {
    /** Push the Waiting Room for [eventId]. */
    data class NavigateToWaitingRoom(val eventId: String) : MyQueuesEffect

    /** Switch to another top-level [tab]. */
    data class SwitchTab(val tab: RusuhTab) : MyQueuesEffect

    /** Go to the Events tab. */
    data object GoToEvents : MyQueuesEffect

    /** Show a transient [message] (snackbar); the rows on screen stay as they are. */
    data class ShowError(val message: MyQueuesMessage) : MyQueuesEffect
}

/** Maps the MVI state onto what the stateless [MyQueuesScreen] renders. */
internal fun MyQueuesState.toUiState(errorMessage: String): MyQueuesUiState =
    when (phase) {
        MyQueuesPhase.Loading -> MyQueuesUiState.Loading
        MyQueuesPhase.Ready -> MyQueuesUiState.Content(queues)
        MyQueuesPhase.Empty -> MyQueuesUiState.Empty
        MyQueuesPhase.Error -> MyQueuesUiState.Error(errorMessage)
    }
