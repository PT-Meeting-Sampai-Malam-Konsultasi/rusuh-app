package com.rusuh.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.rusuh.app.data.network.session.SessionStateProvider

/**
 * Decides whether a user may enter the queue for [eventId].
 *
 * A signed-in user goes straight to the waiting room; a signed-out user goes to sign-in with
 * the event id so that, after signing in, the sign-in route is replaced with the waiting room.
 */
sealed interface QueueEntryDecision {
    data class ToWaitingRoom(val eventId: String) : QueueEntryDecision

    data class ToSignIn(val eventId: String) : QueueEntryDecision
}

fun decideQueueEntry(
    eventId: String,
    isSignedIn: Boolean,
): QueueEntryDecision =
    if (isSignedIn) {
        QueueEntryDecision.ToWaitingRoom(eventId)
    } else {
        QueueEntryDecision.ToSignIn(eventId)
    }

/**
 * Composable helper that reads the session state and returns the entry decision.
 * Uses the same [SessionStateProvider] the ViewModels use, so a token refresh
 * does not bounce a signed-in user.
 */
@Composable
fun rememberQueueEntryDecision(
    eventId: String,
    sessionState: SessionStateProvider,
): QueueEntryDecision {
    val isSignedIn by sessionState.isSignedIn.collectAsState()
    return decideQueueEntry(eventId, isSignedIn)
}
