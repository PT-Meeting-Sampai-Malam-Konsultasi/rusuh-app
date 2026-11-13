package com.rusuh.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for the queue entry decision logic (AUTH-00-M8 #154).
 *
 * The gate ensures users are signed in before joining a queue:
 * - Signed in -> go to waiting room
 * - Signed out -> go to sign-in with event id
 */
class QueueEntryDecisionTest {
    @Test
    fun signedInUserGoesToWaitingRoom() {
        val decision = decideQueueEntry(eventId = "evt_123", isSignedIn = true)
        val toWaitingRoom = assertIs<QueueEntryDecision.ToWaitingRoom>(decision)
        assertEquals("evt_123", toWaitingRoom.eventId)
    }

    @Test
    fun signedOutUserGoesToSignIn() {
        val decision = decideQueueEntry(eventId = "evt_456", isSignedIn = false)
        val toSignIn = assertIs<QueueEntryDecision.ToSignIn>(decision)
        assertEquals("evt_456", toSignIn.eventId)
    }

    @Test
    fun eventIdIsPreservedForSignIn() {
        val eventId = "event-with-special-chars_123"
        val decision = decideQueueEntry(eventId = eventId, isSignedIn = false)
        val toSignIn = assertIs<QueueEntryDecision.ToSignIn>(decision)
        assertEquals(eventId, toSignIn.eventId)
    }

    @Test
    fun eventIdIsPreservedForWaitingRoom() {
        val eventId = "event-with-special-chars_123"
        val decision = decideQueueEntry(eventId = eventId, isSignedIn = true)
        val toWaitingRoom = assertIs<QueueEntryDecision.ToWaitingRoom>(decision)
        assertEquals(eventId, toWaitingRoom.eventId)
    }
}
