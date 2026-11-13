package com.rusuh.app.feature.waitingroom

import com.rusuh.app.ui.components.QueuePositionUi
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.waiting_paused_reason_operator
import rusuh_app.shared.generated.resources.waiting_paused_reason_stock
import rusuh_app.shared.generated.resources.waiting_poster_closed
import rusuh_app.shared.generated.resources.waiting_poster_live
import rusuh_app.shared.generated.resources.waiting_poster_paused
import rusuh_app.shared.generated.resources.waiting_poster_prequeue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaitingRoomScreenTest {
    private val phases =
        mapOf(
            "prequeue" to WaitingRoomPhase.PreQueue(522, "10.00 WIB"),
            "waiting" to WaitingRoomPhase.Waiting(QueuePositionUi(position = 5)),
            "paused" to WaitingRoomPhase.Paused(5, PauseReason.Stock),
            "turn" to WaitingRoomPhase.YourTurn(19_700, 52),
            "missed" to WaitingRoomPhase.MissedTurn(26),
            "soldout" to WaitingRoomPhase.SoldOut,
        )

    @Test
    fun posterTagPerPhase() {
        assertEquals(Res.string.waiting_poster_prequeue, phases.getValue("prequeue").posterTag())
        assertEquals(Res.string.waiting_poster_live, phases.getValue("waiting").posterTag())
        assertEquals(Res.string.waiting_poster_paused, phases.getValue("paused").posterTag())
        assertEquals(Res.string.waiting_poster_live, phases.getValue("turn").posterTag())
        assertEquals(Res.string.waiting_poster_live, phases.getValue("missed").posterTag())
        assertEquals(Res.string.waiting_poster_closed, phases.getValue("soldout").posterTag())
    }

    @Test
    fun pauseReasonMapsToCopy() {
        assertEquals(Res.string.waiting_paused_reason_stock, PauseReason.Stock.messageRes())
        assertEquals(Res.string.waiting_paused_reason_operator, PauseReason.Operator.messageRes())
    }

    @Test
    fun notesOnlyWhileInLine() {
        listOf("prequeue", "waiting", "paused").forEach {
            assertTrue(phases.getValue(it).showsNotes(), it)
        }
        listOf("turn", "missed", "soldout").forEach {
            assertFalse(phases.getValue(it).showsNotes(), it)
        }
    }
}
