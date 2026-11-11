package com.rusuh.app.feature.waitingroom

import com.rusuh.app.feature.waitingroom.domain.model.QueuePauseReason
import com.rusuh.app.feature.waitingroom.domain.model.QueuePlace
import com.rusuh.app.feature.waitingroom.domain.model.QueueState
import com.rusuh.app.ui.components.QueuePhase
import com.rusuh.app.ui.components.QueuePositionUi

private const val MILLIS_PER_SECOND = 1_000L

/**
 * People ahead of the customer plus the customer: `seq - served`, never below 1
 * (a place at or behind the frontier is shown as next in line, never negative).
 */
internal fun QueuePlace.position(): Int = (seq - served).coerceAtLeast(1)

/**
 * Whole seconds from the server "now" to [targetEpochMillis], rounded up, clamped to
 * `0..Int.MAX_VALUE`. The server "now" is the snapshot's own server time plus
 * [elapsedMillis] of monotonic time since it was received; never the device wall clock.
 */
private fun QueuePlace.secondsUntil(
    targetEpochMillis: Long,
    elapsedMillis: Long,
): Int {
    val remaining = (targetEpochMillis - (serverNowEpochMillis + elapsedMillis)).coerceAtLeast(0L)
    return ((remaining + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** Estimated wait in whole minutes (rounded up) from the rate; null when the rate is unknown. */
private fun QueuePlace.etaMinutes(): Int? {
    val rate = ratePerMinute?.takeIf { it > 0 } ?: return null
    return (position() + rate - 1) / rate
}

private fun QueuePauseReason?.toUi(): PauseReason =
    when (this) {
        QueuePauseReason.OperatorCheck -> PauseReason.Operator
        QueuePauseReason.StockFull, null -> PauseReason.Stock
    }

/**
 * The [WaitingRoomPhase] this place renders as, or null when the snapshot is invalid
 * (a pre-open without `opensAt`, or a turn without `turnExpiresAt`: a countdown would
 * be meaningless, so it must not render as 0:00).
 */
internal fun QueuePlace.toPhase(elapsedMillis: Long = 0L): WaitingRoomPhase? =
    when (state) {
        QueueState.PreOpen ->
            opensAtEpochMillis?.let { WaitingRoomPhase.PreQueue(secondsUntil(it, elapsedMillis), opensAtLabel) }
        QueueState.Open ->
            WaitingRoomPhase.Waiting(
                QueuePositionUi(
                    position = position(),
                    phase = QueuePhase.Waiting,
                    estimatedWaitMinutes = etaMinutes(),
                    ratePerMinute = ratePerMinute,
                ),
            )
        QueueState.Paused -> WaitingRoomPhase.Paused(position(), pauseReason.toUi())
        QueueState.Turn ->
            turnExpiresAtEpochMillis?.let {
                WaitingRoomPhase.YourTurn(seq.coerceAtLeast(0), secondsUntil(it, elapsedMillis))
            }
        QueueState.Missed -> WaitingRoomPhase.MissedTurn(rejoinEstimateMinutes)
        QueueState.Closed -> WaitingRoomPhase.SoldOut
    }

/**
 * Maps a domain [QueuePlace] to what the screen renders, [elapsedMillis] of monotonic time
 * after it was received. An invalid snapshot becomes a blank-message [WaitingRoomUiState.Error].
 *
 * shortcut: `isGuest` stays false until the auth state seam (AUTH-00) exists.
 */
fun QueuePlace.toUiState(elapsedMillis: Long = 0L): WaitingRoomUiState {
    val phase = toPhase(elapsedMillis) ?: return WaitingRoomUiState.Error("")
    return WaitingRoomUiState.Active(
        event =
            WaitingRoomEventUi(
                title = event.title,
                subtitle = event.subtitle,
                dateTimeLine = event.dateTimeLine,
                venueLine = event.venueLine,
            ),
        phase = phase,
    )
}
