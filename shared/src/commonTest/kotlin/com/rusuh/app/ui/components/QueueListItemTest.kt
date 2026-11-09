package com.rusuh.app.ui.components

import androidx.compose.ui.graphics.Color
import com.rusuh.app.ui.theme.RusuhBrandColors
import com.rusuh.app.ui.theme.RusuhStatusColors
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.queue_list_item_now_serving
import rusuh_app.shared.generated.resources.queue_list_item_open_hint
import rusuh_app.shared.generated.resources.queue_list_item_paused_hint
import rusuh_app.shared.generated.resources.queue_list_item_phase_closed
import rusuh_app.shared.generated.resources.queue_list_item_phase_live
import rusuh_app.shared.generated.resources.queue_list_item_phase_open
import rusuh_app.shared.generated.resources.queue_list_item_phase_paused
import rusuh_app.shared.generated.resources.queue_list_item_phase_your_turn
import rusuh_app.shared.generated.resources.queue_list_item_updated_hours
import rusuh_app.shared.generated.resources.queue_list_item_updated_minutes
import rusuh_app.shared.generated.resources.queue_list_item_updated_seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class QueueListItemTest {
    private fun item(
        phase: QueueListPhase,
        position: Int = 10,
        nowServing: Int? = 5,
        updatedAgoSeconds: Int = 3,
    ) = QueueListItemUi(
        eventId = "evt-1",
        title = "Samudra",
        dateVenue = "12 Des 2026 · Jakarta",
        position = position,
        nowServing = nowServing,
        phase = phase,
        updatedAgoSeconds = updatedAgoSeconds,
    )

    @Test
    fun phaseLabelsMapToStringResources() {
        assertEquals(Res.string.queue_list_item_phase_open, QueueListPhase.QueueOpen.phaseLabel())
        assertEquals(Res.string.queue_list_item_phase_live, QueueListPhase.Live.phaseLabel())
        assertEquals(Res.string.queue_list_item_phase_paused, QueueListPhase.Paused.phaseLabel())
        assertEquals(Res.string.queue_list_item_phase_your_turn, QueueListPhase.YourTurn.phaseLabel())
        assertEquals(Res.string.queue_list_item_phase_closed, QueueListPhase.Closed.phaseLabel())
    }

    @Test
    fun phaseColorsMapToStampPalette() {
        assertEquals(RusuhStatusColors.upcoming, QueueListPhase.QueueOpen.phaseColors())
        assertEquals(RusuhBrandColors.Pink, QueueListPhase.Live.phaseColors().container)
        assertEquals(Color.White, QueueListPhase.Live.phaseColors().content)
        assertEquals(RusuhStatusColors.limited, QueueListPhase.Paused.phaseColors())
        assertEquals(RusuhStatusColors.available, QueueListPhase.YourTurn.phaseColors())
        assertEquals(RusuhBrandColors.Ink, QueueListPhase.Closed.phaseColors().container)
        assertEquals(RusuhBrandColors.Paper, QueueListPhase.Closed.phaseColors().content)
    }

    @Test
    fun updatedAgoPicksSecondsMinutesHours() {
        assertEquals(UpdatedAgoLabel(Res.string.queue_list_item_updated_seconds, 0), updatedAgoLabel(0))
        assertEquals(UpdatedAgoLabel(Res.string.queue_list_item_updated_seconds, 59), updatedAgoLabel(59))
        assertEquals(UpdatedAgoLabel(Res.string.queue_list_item_updated_minutes, 1), updatedAgoLabel(60))
        assertEquals(UpdatedAgoLabel(Res.string.queue_list_item_updated_minutes, 59), updatedAgoLabel(3599))
        assertEquals(UpdatedAgoLabel(Res.string.queue_list_item_updated_hours, 2), updatedAgoLabel(7200))
    }

    @Test
    fun queueNumbersGetThousandsSeparators() {
        assertEquals("7", formatQueueNumber(7))
        assertEquals("128", formatQueueNumber(128))
        assertEquals("1.248", formatQueueNumber(1248))
        assertEquals("2.041", formatQueueNumber(2041))
        assertEquals("1.234.567", formatQueueNumber(1234567))
    }

    @Test
    fun contextLineDependsOnPhase() {
        assertEquals(Res.string.queue_list_item_now_serving, contextLineResource(item(QueueListPhase.Live)))
        assertNull(contextLineResource(item(QueueListPhase.Live, nowServing = null)))
        assertEquals(Res.string.queue_list_item_open_hint, contextLineResource(item(QueueListPhase.QueueOpen)))
        assertEquals(Res.string.queue_list_item_paused_hint, contextLineResource(item(QueueListPhase.Paused)))
    }

    @Test
    fun positionMustBeAtLeastOne() {
        assertFailsWith<IllegalArgumentException> { item(QueueListPhase.Live, position = 0) }
    }

    @Test
    fun updatedAgoMustNotBeNegative() {
        assertFailsWith<IllegalArgumentException> { item(QueueListPhase.Live, updatedAgoSeconds = -1) }
    }

    @Test
    fun eventIdMustNotBeBlank() {
        assertFailsWith<IllegalArgumentException> {
            QueueListItemUi(
                eventId = " ",
                title = "X",
                dateVenue = "y",
                position = 1,
                nowServing = null,
                phase = QueueListPhase.Live,
                updatedAgoSeconds = 0,
            )
        }
    }
}
