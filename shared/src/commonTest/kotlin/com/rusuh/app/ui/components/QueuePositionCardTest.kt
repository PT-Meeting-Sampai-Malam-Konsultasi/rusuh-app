package com.rusuh.app.ui.components

import com.rusuh.app.ui.theme.RusuhStatusColors
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.queue_people_ahead_count
import rusuh_app.shared.generated.resources.queue_people_ahead_none
import rusuh_app.shared.generated.resources.queue_people_ahead_sold_out
import rusuh_app.shared.generated.resources.queue_position_closed
import rusuh_app.shared.generated.resources.queue_position_waiting
import rusuh_app.shared.generated.resources.queue_position_your_turn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QueuePositionCardTest {
    private fun data(
        position: Int = 42,
        peopleAhead: Int = 41,
        phase: QueuePhase = QueuePhase.Waiting,
        eta: String? = "~8 menit",
    ) = QueuePositionUi(position = position, peopleAhead = peopleAhead, phase = phase, estimatedWait = eta)

    @Test
    fun accentColorsMapPerPhase() {
        assertEquals(RusuhStatusColors.upcoming, QueuePhase.Waiting.accentColors())
        assertEquals(RusuhStatusColors.available, QueuePhase.YourTurn.accentColors())
        assertEquals(RusuhStatusColors.soldOut, QueuePhase.QueueClosed.accentColors())
    }

    @Test
    fun positionLabelPerPhase() {
        assertEquals(Res.string.queue_position_waiting, QueuePhase.Waiting.positionLabel())
        assertEquals(Res.string.queue_position_your_turn, QueuePhase.YourTurn.positionLabel())
        assertEquals(Res.string.queue_position_closed, QueuePhase.QueueClosed.positionLabel())
    }

    @Test
    fun peopleAheadLabelCountsWhileWaiting() {
        assertEquals(
            PeopleAheadLabel(Res.string.queue_people_ahead_count, 41),
            peopleAheadLabel(data(peopleAhead = 41)),
        )
    }

    @Test
    fun peopleAheadLabelZeroWhileWaiting() {
        assertEquals(
            PeopleAheadLabel(Res.string.queue_people_ahead_none),
            peopleAheadLabel(data(peopleAhead = 0)),
        )
    }

    @Test
    fun peopleAheadLabelYourTurnIgnoresCount() {
        assertEquals(
            PeopleAheadLabel(Res.string.queue_people_ahead_none),
            peopleAheadLabel(data(peopleAhead = 5, phase = QueuePhase.YourTurn)),
        )
    }

    @Test
    fun peopleAheadLabelClosedShowsSoldOut() {
        assertEquals(
            PeopleAheadLabel(Res.string.queue_people_ahead_sold_out),
            peopleAheadLabel(data(phase = QueuePhase.QueueClosed)),
        )
    }

    @Test
    fun positionMustBeAtLeastOne() {
        assertFailsWith<IllegalArgumentException> { data(position = 0) }
    }

    @Test
    fun peopleAheadMustBeNonNegative() {
        assertFailsWith<IllegalArgumentException> { data(peopleAhead = -1) }
    }

    @Test
    fun peopleAheadCannotExceedPositionMinusOne() {
        assertFailsWith<IllegalArgumentException> { data(position = 3, peopleAhead = 3) }
    }
}
