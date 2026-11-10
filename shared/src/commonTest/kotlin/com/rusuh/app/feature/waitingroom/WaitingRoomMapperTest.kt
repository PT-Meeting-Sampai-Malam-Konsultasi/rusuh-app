package com.rusuh.app.feature.waitingroom

import com.rusuh.app.feature.waitingroom.domain.model.QueueEventInfo
import com.rusuh.app.feature.waitingroom.domain.model.QueuePauseReason
import com.rusuh.app.feature.waitingroom.domain.model.QueuePlace
import com.rusuh.app.feature.waitingroom.domain.model.QueueState
import com.rusuh.app.ui.components.QueuePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WaitingRoomMapperTest {
    private fun place(
        state: QueueState,
        seq: Int = 128,
        served: Int = 20,
        block: (QueuePlace) -> QueuePlace = { it },
    ) = block(
        QueuePlace(
            eventId = "e1",
            event = QueueEventInfo(title = "Konser", subtitle = "Sub", dateTimeLine = "Sab", venueLine = "GBK"),
            state = state,
            seq = seq,
            served = served,
            serverNowEpochMillis = 10_000L,
        ),
    )

    @Test
    fun preOpenCountsDownFromServerTimeRoundedUp() {
        val preOpen = place(QueueState.PreOpen) { it.copy(opensAtEpochMillis = 12_500L, opensAtLabel = "10.00 WIB") }
        val phase = preOpen.toPhase()
        assertEquals(WaitingRoomPhase.PreQueue(secondsUntilOpen = 3, opensAtLabel = "10.00 WIB"), phase)
    }

    @Test
    fun preOpenInThePastClampsToZero() {
        val phase = place(QueueState.PreOpen) { it.copy(opensAtEpochMillis = 1_000L) }.toPhase()
        assertEquals(0, assertIs<WaitingRoomPhase.PreQueue>(phase).secondsUntilOpen)
    }

    @Test
    fun openMapsPositionAndEtaFromRate() {
        val phase = place(QueueState.Open) { it.copy(ratePerMinute = 20) }.toPhase()
        val queue = assertIs<WaitingRoomPhase.Waiting>(phase).queue
        assertEquals(108, queue.position)
        assertEquals(6, queue.estimatedWaitMinutes)
        assertEquals(20, queue.ratePerMinute)
        assertEquals(QueuePhase.Waiting, queue.phase)
    }

    @Test
    fun openWithoutRateHasNoEta() {
        val queue = assertIs<WaitingRoomPhase.Waiting>(place(QueueState.Open).toPhase()).queue
        assertEquals(null, queue.estimatedWaitMinutes)
    }

    @Test
    fun pausedKeepsPositionAndReason() {
        val phase = place(QueueState.Paused) { it.copy(pauseReason = QueuePauseReason.OperatorCheck) }.toPhase()
        assertEquals(WaitingRoomPhase.Paused(position = 108, reason = PauseReason.Operator), phase)
    }

    @Test
    fun pausedWithoutReasonDefaultsToStock() {
        val phase = assertIs<WaitingRoomPhase.Paused>(place(QueueState.Paused).toPhase())
        assertEquals(PauseReason.Stock, phase.reason)
    }

    @Test
    fun turnUsesServerExpiryNotDeviceClock() {
        val phase = place(QueueState.Turn) { it.copy(turnExpiresAtEpochMillis = 70_000L) }.toPhase()
        assertEquals(WaitingRoomPhase.YourTurn(myNumber = 128, secondsLeft = 60), phase)
    }

    @Test
    fun expiredTurnNeverGoesNegative() {
        val phase = place(QueueState.Turn) { it.copy(turnExpiresAtEpochMillis = 1L) }.toPhase()
        assertEquals(0, assertIs<WaitingRoomPhase.YourTurn>(phase).secondsLeft)
    }

    @Test
    fun missedCarriesRejoinEstimate() {
        val phase = place(QueueState.Missed) { it.copy(rejoinEstimateMinutes = 12) }.toPhase()
        assertEquals(WaitingRoomPhase.MissedTurn(estimateMinutes = 12), phase)
    }

    @Test
    fun closedIsSoldOut() {
        assertEquals(WaitingRoomPhase.SoldOut, place(QueueState.Closed).toPhase())
    }

    @Test
    fun positionIsNeverNegativeOrZero() {
        listOf(place(QueueState.Open, seq = 5, served = 9), place(QueueState.Open, seq = 5, served = 5)).forEach {
            assertEquals(1, it.position())
        }
        val paused = assertIs<WaitingRoomPhase.Paused>(place(QueueState.Paused, seq = 3, served = 50).toPhase())
        assertTrue(paused.position >= 1)
    }

    @Test
    fun toUiStateCarriesEventCopy() {
        val ui = assertIs<WaitingRoomUiState.Active>(place(QueueState.Closed).toUiState())
        assertEquals(WaitingRoomEventUi("Konser", "Sub", "Sab", "GBK"), ui.event)
        assertEquals(false, ui.isGuest)
    }

    @Test
    fun turnWithoutExpiryIsInvalidNotZero() {
        val place = place(QueueState.Turn)
        assertEquals(null, place.toPhase())
        assertIs<WaitingRoomUiState.Error>(place.toUiState())
    }

    @Test
    fun preOpenWithoutOpensAtIsInvalid() {
        assertIs<WaitingRoomUiState.Error>(place(QueueState.PreOpen).toUiState())
    }

    @Test
    fun elapsedMonotonicTimeAdvancesCountdown() {
        val turn = place(QueueState.Turn) { it.copy(turnExpiresAtEpochMillis = 70_000L) }
        assertEquals(60, assertIs<WaitingRoomPhase.YourTurn>(turn.toPhase(0)).secondsLeft)
        assertEquals(57, assertIs<WaitingRoomPhase.YourTurn>(turn.toPhase(3_000)).secondsLeft)
        assertEquals(0, assertIs<WaitingRoomPhase.YourTurn>(turn.toPhase(90_000)).secondsLeft)
    }

    @Test
    fun hugeDeadlineClampsSeconds() {
        val turn = place(QueueState.Turn) { it.copy(turnExpiresAtEpochMillis = Long.MAX_VALUE) }
        assertEquals(Int.MAX_VALUE, assertIs<WaitingRoomPhase.YourTurn>(turn.toPhase()).secondsLeft)
    }
}
