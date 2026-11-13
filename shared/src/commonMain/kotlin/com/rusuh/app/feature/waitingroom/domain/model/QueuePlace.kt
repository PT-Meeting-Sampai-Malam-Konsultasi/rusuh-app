package com.rusuh.app.feature.waitingroom.domain.model

/** Lifecycle of a customer's place in an event queue (API: `QueuePlace` / `Frontier` state). */
enum class QueueState {
    /** The queue has not opened yet. */
    PreOpen,

    /** In line and moving. */
    Open,

    /** Queue halted; the place is kept. */
    Paused,

    /** Tickets sold out / queue closed. */
    Closed,

    /** It is the customer's turn to enter. */
    Turn,

    /** The turn window ran out. */
    Missed,
}

/** Why a queue is paused. Domain twin of the UI pause reason. */
enum class QueuePauseReason {
    StockFull,
    OperatorCheck,
}

/**
 * Event copy shown on the Waiting Room poster. Pre-formatted by the data layer,
 * like the Home event card.
 */
data class QueueEventInfo(
    val title: String,
    val subtitle: String = "",
    val dateTimeLine: String = "",
    val venueLine: String = "",
)

/**
 * A snapshot of the customer's place in the queue for one event.
 *
 * All times are epoch milliseconds on the SERVER clock. Countdowns derive from
 * `expiresAt - serverNow` ([serverNowEpochMillis]), never from the device clock,
 * so a skewed device cannot change when a turn expires.
 *
 * @property seq the customer's sequence number in the queue (`QueuePlace.seq`).
 * @property served the last sequence number admitted so far (`Frontier.served`).
 * @property serverNowEpochMillis server time when this snapshot was produced.
 * @property opensAtEpochMillis when the queue opens (pre-open only).
 * @property opensAtLabel pre-formatted opening time, e.g. "10.00 WIB".
 * @property turnExpiresAtEpochMillis when the turn window ends (turn only).
 * @property ratePerMinute people admitted per minute; null when unknown.
 * @property pauseReason why the queue is paused (paused only).
 * @property rejoinEstimateMinutes estimated wait if the customer queues again (missed only).
 */
data class QueuePlace(
    val eventId: String,
    val event: QueueEventInfo,
    val state: QueueState,
    val seq: Int,
    val served: Int,
    val servedAtJoin: Int? = null,
    val serverNowEpochMillis: Long,
    val opensAtEpochMillis: Long? = null,
    val opensAtLabel: String = "",
    val turnExpiresAtEpochMillis: Long? = null,
    val ratePerMinute: Int? = null,
    val pauseReason: QueuePauseReason? = null,
    val rejoinEstimateMinutes: Int? = null,
)
