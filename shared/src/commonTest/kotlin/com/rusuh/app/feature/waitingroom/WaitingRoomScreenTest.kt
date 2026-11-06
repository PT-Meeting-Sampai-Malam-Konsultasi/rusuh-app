package com.rusuh.app.feature.waitingroom

import com.rusuh.app.ui.components.QueuePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaitingRoomScreenTest {
    @Test
    fun statusMessagePerPhase() {
        assertEquals("Menunggu giliran…", QueuePhase.Waiting.statusMessage())
        assertEquals("Giliran Anda tiba", QueuePhase.YourTurn.statusMessage())
        assertTrue(QueuePhase.QueueClosed.statusMessage().contains("habis terjual"))
    }
}
