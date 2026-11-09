package com.rusuh.app.ui.components

import androidx.compose.ui.graphics.Color
import com.rusuh.app.ui.theme.RusuhBrandColors
import com.rusuh.app.ui.theme.RusuhStatusColors
import com.rusuh.app.ui.theme.StatusColorPair
import org.jetbrains.compose.resources.StringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.queue_list_item_closed_hint
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
import rusuh_app.shared.generated.resources.queue_list_item_your_turn_hint

// Pure, rendering-free helpers for QueueListItem (unit-tested in commonTest).

/** Short stamp label resource for a queue phase. Pure function, unit-tested directly. */
fun QueueListPhase.phaseLabel(): StringResource =
    when (this) {
        QueueListPhase.QueueOpen -> Res.string.queue_list_item_phase_open
        QueueListPhase.Live -> Res.string.queue_list_item_phase_live
        QueueListPhase.Paused -> Res.string.queue_list_item_phase_paused
        QueueListPhase.YourTurn -> Res.string.queue_list_item_phase_your_turn
        QueueListPhase.Closed -> Res.string.queue_list_item_phase_closed
    }

/**
 * Stamp colors for a queue phase (hi-fi `.stamp` variants): live = pink,
 * open = blue, paused = orange, your turn = green, closed = solid ink.
 * Pure function, unit-tested directly.
 */
fun QueueListPhase.phaseColors(): StatusColorPair =
    when (this) {
        QueueListPhase.QueueOpen -> RusuhStatusColors.upcoming
        QueueListPhase.Live -> StatusColorPair(RusuhBrandColors.Pink, Color.White)
        QueueListPhase.Paused -> RusuhStatusColors.limited
        QueueListPhase.YourTurn -> RusuhStatusColors.available
        QueueListPhase.Closed -> StatusColorPair(RusuhBrandColors.Ink, RusuhBrandColors.Paper)
    }

/** Resource (and count arg) for the freshness stamp. */
internal data class UpdatedAgoLabel(
    val resource: StringResource,
    val count: Int,
)

/** Picks seconds / minutes / hours for the "updated ago" stamp. Pure function, unit-tested. */
internal fun updatedAgoLabel(seconds: Int): UpdatedAgoLabel =
    when {
        seconds < SECONDS_PER_MINUTE -> UpdatedAgoLabel(Res.string.queue_list_item_updated_seconds, seconds)
        seconds < SECONDS_PER_HOUR ->
            UpdatedAgoLabel(Res.string.queue_list_item_updated_minutes, seconds / SECONDS_PER_MINUTE)
        else -> UpdatedAgoLabel(Res.string.queue_list_item_updated_hours, seconds / SECONDS_PER_HOUR)
    }

/** True for phases where the user's numeric position is meaningful and displayed. */
internal fun QueueListPhase.showsPosition(): Boolean = this != QueueListPhase.YourTurn && this != QueueListPhase.Closed

/** ETA minutes to show on the row: only for live queues with a known estimate. */
internal fun visibleEtaMinutes(data: QueueListItemUi): Int? =
    if (data.phase == QueueListPhase.Live) data.etaMinutes else null

/**
 * Joins a lead-in and a number: "Anda antrian ke-" glues to the number, while
 * "You are number" needs a space. Mirrors QueuePositionCard's description.
 */
internal fun joinLeadAndNumber(
    lead: String,
    number: String,
): String {
    val trimmed = lead.trimEnd()
    val separator = if (trimmed.endsWith("-")) "" else " "
    return "$trimmed$separator$number"
}

/** Context sentence under the position: now-serving, or a phase hint. */
internal fun contextLineResource(data: QueueListItemUi): StringResource? =
    when (data.phase) {
        QueueListPhase.Live -> data.nowServing?.let { Res.string.queue_list_item_now_serving }
        QueueListPhase.QueueOpen -> Res.string.queue_list_item_open_hint
        QueueListPhase.Paused -> Res.string.queue_list_item_paused_hint
        QueueListPhase.YourTurn -> Res.string.queue_list_item_your_turn_hint
        QueueListPhase.Closed -> Res.string.queue_list_item_closed_hint
    }

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
