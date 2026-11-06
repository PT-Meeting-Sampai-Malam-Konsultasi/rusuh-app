package com.rusuh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.rusuh.app.ui.theme.RusuhStatusColors
import com.rusuh.app.ui.theme.StatusColorPair

/**
 * Availability status of an event, as shown on the home page event cards.
 *
 * Each variant carries the data its badge needs so the UI never has to reach
 * back into the event model. Keep rendering-free so it can be unit-tested in
 * commonTest without a Compose harness.
 */
sealed interface EventStatus {
    /** Tickets are on sale and can be bought now. */
    data object Available : EventStatus

    /** Tickets held by other buyers; may free up. Label: "Penuh sementara". */
    data object TemporarilyFull : EventStatus

    /**
     * Everything sold. Shows how fast it went and the venue capacity.
     *
     * @param soldOutInMinutes minutes from sale-open to sell-out (e.g. 41).
     * @param capacity total tickets sold / venue capacity (e.g. 12_000).
     */
    data class SoldOut(val soldOutInMinutes: Int, val capacity: Int) : EventStatus

    /**
     * Not on sale yet. Shows a countdown to when sales open.
     *
     * @param countdown pre-formatted remaining time (e.g. "7 hari 14 jam lagi").
     *   Pre-formatted by the caller so this stays locale/clock agnostic.
     */
    data class ComingSoon(val countdown: String) : EventStatus
}

/**
 * Resolves an [EventStatus] to its short pill label and semantic colors.
 * Pure function — unit-tested directly.
 */
fun EventStatus.badgeLabel(): String =
    when (this) {
        EventStatus.Available -> "Antrian dibuka"
        EventStatus.TemporarilyFull -> "Penuh sementara"
        is EventStatus.SoldOut -> "Habis terjual"
        is EventStatus.ComingSoon -> "Segera dijual"
    }

/** Semantic color pair for the status. Pure function — unit-tested directly. */
fun EventStatus.badgeColors(): StatusColorPair =
    when (this) {
        EventStatus.Available -> RusuhStatusColors.available
        EventStatus.TemporarilyFull -> RusuhStatusColors.limited
        is EventStatus.SoldOut -> RusuhStatusColors.soldOut
        is EventStatus.ComingSoon -> RusuhStatusColors.upcoming
    }

/**
 * A small pill badge showing event availability.
 *
 * Available shows a leading status dot (per the mockup); the badge as a whole
 * carries a single content description so screen readers announce the status
 * once rather than reading the dot and label separately.
 */
@Composable
fun StatusBadge(
    status: EventStatus,
    modifier: Modifier = Modifier,
) {
    val colors = status.badgeColors()
    val label = status.badgeLabel()
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = "Status: $label" },
        color = colors.container,
        contentColor = colors.content,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (status is EventStatus.Available) {
                StatusDot(color = colors.content)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier =
            modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape),
    )
}
