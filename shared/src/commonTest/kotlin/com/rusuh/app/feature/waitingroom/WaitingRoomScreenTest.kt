package com.rusuh.app.feature.waitingroom

import com.rusuh.app.ui.components.QueuePhase
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.waiting_status_closed
import rusuh_app.shared.generated.resources.waiting_status_waiting
import rusuh_app.shared.generated.resources.waiting_status_your_turn
import kotlin.test.Test
import kotlin.test.assertEquals

class WaitingRoomScreenTest {
    @Test
    fun statusMessagePerPhase() {
        assertEquals(Res.string.waiting_status_waiting, QueuePhase.Waiting.statusMessage())
        assertEquals(Res.string.waiting_status_your_turn, QueuePhase.YourTurn.statusMessage())
        assertEquals(Res.string.waiting_status_closed, QueuePhase.QueueClosed.statusMessage())
    }
}
