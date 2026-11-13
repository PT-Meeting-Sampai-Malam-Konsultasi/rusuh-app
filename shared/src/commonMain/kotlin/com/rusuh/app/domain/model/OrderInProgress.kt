package com.rusuh.app.domain.model

/** A resumable order window or held payment. */
data class OrderInProgress(
    val eventId: String,
    val bookingId: String? = null,
    val remainingMillis: Long?,
)
