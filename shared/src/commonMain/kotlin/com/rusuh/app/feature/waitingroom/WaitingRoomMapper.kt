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

/** Whole seconds until [targetEpochMillis] on the server clock, rounded up, never negative. */
private fun QueuePlace.secondsUntil(targetEpochMillis: Long?): Int {
    val remaining = ((targetEpochMillis ?: serverNowEpochMillis) - serverNowEpochMillis).coerceAtLeast(0L)
    return ((remaining + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
}

/** Estimated wait in whole minutes (rounded up) from [ratePerMinute]; null when the rate is unknown. */
private fun QueuePlace.etaMinutes(): Int? =
    ratePerMinute?.takeIf { it > 0 }?.let { rate -> (position() + rate - 1) / rate }

private fun QueuePauseReason?.toUi(): PauseReason =
    when (this) {
        QueuePauseReason.OperatorCheck -> PauseReason.Operator
        QueuePauseReason.StockFull, null -> PauseReason.Stock
    }

/** The [WaitingRoomPhase] this place renders as. */
internal fun QueuePlace.toPhase(): WaitingRoomPhase =
    when (state) {
        QueueState.PreOpen -> WaitingRoomPhase.PreQueue(secondsUntil(opensAtEpochMillis), opensAtLabel)
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
        QueueState.Turn -> WaitingRoomPhase.YourTurn(seq.coerceAtLeast(0), secondsUntil(turnExpiresAtEpochMillis))
        QueueState.Missed -> WaitingRoomPhase.MissedTurn(rejoinEstimateMinutes)
        QueueState.Closed -> WaitingRoomPhase.SoldOut
    }

/**
 * Maps a domain [QueuePlace] to what the screen renders.
 *
 * shortcut: `isGuest` stays false until the auth state seam (AUTH-00) exists.
 */
fun QueuePlace.toUiState(): WaitingRoomUiState.Active =
    WaitingRoomUiState.Active(
        event =
            WaitingRoomEventUi(
                title = event.title,
                subtitle = event.subtitle,
                dateTimeLine = event.dateTimeLine,
                venueLine = event.venueLine,
            ),
        phase = toPhase(),
    )
