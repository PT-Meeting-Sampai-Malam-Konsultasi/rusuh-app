package com.rusuh.app.feature.waitingroom.data.repository

import com.rusuh.app.feature.waitingroom.domain.model.QueueEventInfo
import com.rusuh.app.feature.waitingroom.domain.model.QueuePauseReason
import com.rusuh.app.feature.waitingroom.domain.model.QueuePlace
import com.rusuh.app.feature.waitingroom.domain.model.QueueState
import com.rusuh.app.feature.waitingroom.domain.repository.WaitingRoomRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * Scripted, deterministic [WaitingRoomRepository] used until FR-04-M4 (#30) ships the
 * real queue API. It walks every phase once: pre-open, waiting, paused, waiting, your
 * turn, missed, sold out, then completes. A blank event id has no place (empty flow).
 *
 * @param stepDelayMillis pause between snapshots; use 0 (or virtual time) in tests.
 * @param serverStartMillis server time of the first snapshot; each step adds one second.
 *
 * shortcut: the poster copy is generated from the event id; swap for the real event data in M4.
 * TODO(FR-04-M4 #30): replace with the network-backed repository (polling + SSE for the last 2,000 places).
 */
class StubWaitingRoomRepository(
    private val stepDelayMillis: Long = DEFAULT_STEP_DELAY_MILLIS,
    private val serverStartMillis: Long = 0L,
) : WaitingRoomRepository {
    override fun observePlace(eventId: String): Flow<QueuePlace> =
        if (eventId.isBlank()) {
            emptyFlow()
        } else {
            flow {
                script(eventId).forEachIndexed { index, place ->
                    if (index > 0) delay(stepDelayMillis)
                    emit(place)
                }
            }
        }

    private fun script(eventId: String): List<QueuePlace> {
        val opensAt = serverStartMillis + OPENS_AFTER_MILLIS
        val turnStart = opensAt + TURN_AFTER_MILLIS
        val turnExpiresAt = turnStart + TURN_WINDOW_MILLIS

        fun place(
            serverNow: Long,
            state: QueueState,
            served: Int,
        ) = QueuePlace(
            eventId = eventId,
            event = QueueEventInfo(title = eventId),
            state = state,
            seq = MY_SEQ,
            served = served,
            serverNowEpochMillis = serverNow,
        )

        // Server time only moves forward: Open starts at/after opensAt, Missed after turnExpiresAt.
        fun preOpen(serverNow: Long) =
            place(serverNow, QueueState.PreOpen, 0)
                .copy(opensAtEpochMillis = opensAt, opensAtLabel = OPENS_AT_LABEL)

        fun open(
            serverNow: Long,
            served: Int,
        ) = place(serverNow, QueueState.Open, served).copy(ratePerMinute = RATE)

        fun turn(serverNow: Long) =
            place(serverNow, QueueState.Turn, MY_SEQ - 1).copy(turnExpiresAtEpochMillis = turnExpiresAt)

        return listOf(
            preOpen(serverStartMillis),
            preOpen(serverStartMillis + STEP_MILLIS),
            open(opensAt, 20),
            open(opensAt + STEP_MILLIS, 60),
            place(opensAt + 2 * STEP_MILLIS, QueueState.Paused, 60).copy(pauseReason = QueuePauseReason.StockFull),
            open(opensAt + 3 * STEP_MILLIS, 100),
            turn(turnStart),
            turn(turnStart + STEP_MILLIS),
            place(turnExpiresAt + STEP_MILLIS, QueueState.Missed, MY_SEQ).copy(rejoinEstimateMinutes = REJOIN_ESTIMATE),
            place(turnExpiresAt + 2 * STEP_MILLIS, QueueState.Closed, MY_SEQ),
        )
    }

    private companion object {
        const val DEFAULT_STEP_DELAY_MILLIS = 1_500L
        const val STEP_MILLIS = 1_000L
        const val TURN_WINDOW_MILLIS = 60_000L
        const val OPENS_AFTER_MILLIS = 2_000L
        const val TURN_AFTER_MILLIS = 5_000L

        // shortcut: fixed display label; real data formats the opening time in M4.
        const val OPENS_AT_LABEL = "10.00 WIB"
        const val MY_SEQ = 128
        const val RATE = 20
        const val REJOIN_ESTIMATE = 12
    }
}
