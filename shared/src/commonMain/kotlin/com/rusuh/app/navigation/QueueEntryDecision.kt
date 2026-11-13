package com.rusuh.app.navigation

/** Signed in goes straight to the waiting room of [eventId]; signed out goes to sign-in for that event first. */
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
