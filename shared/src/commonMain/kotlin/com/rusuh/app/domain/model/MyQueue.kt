package com.rusuh.app.domain.model

/** Lifecycle of a queue the user has joined (domain mirror of the row phases). */
enum class MyQueuePhase { QueueOpen, Live, Paused, YourTurn, Closed }

/**
 * One queue the signed-in user is in, with display-ready event facts and raw live
 * numbers.
 *
 * shortcut: shape is our own guess until the my-queues API contract is frozen
 * (FR-APP-01-M4 #50); fields are NOT validated here, the UI mapper validates.
 */
data class MyQueue(
    val eventId: String,
    val title: String,
    val dateVenue: String,
    val position: Int,
    val nowServing: Int?,
    val phase: MyQueuePhase,
    val updatedAgoSeconds: Int,
    val etaMinutes: Int? = null,
)
