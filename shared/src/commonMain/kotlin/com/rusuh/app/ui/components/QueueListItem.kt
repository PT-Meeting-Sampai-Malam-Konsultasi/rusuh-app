package com.rusuh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.RusuhStatusColors
import com.rusuh.app.ui.theme.StatusColorPair

/**
 * Live phase of a queue the user is in, as shown on a My Queues row. Richer
 * than the Waiting Room's [QueuePhase]: the list surfaces the queue's own
 * lifecycle (open / live / paused / closed) alongside the "your turn" moment,
 * because a user sees many queues at once and needs to tell them apart before
 * opening one.
 *
 * Kept rendering-free so the phase->label/color mapping is unit-testable in
 * commonTest without a Compose harness (mirrors [EventStatus] / [QueuePhase]).
 */
enum class QueueListPhase {
    /** Queue announced but not yet admitting — positions not moving yet. */
    QueueOpen,

    /** Queue is actively admitting; the line is moving. */
    Live,

    /** Temporarily halted by the organiser; positions frozen. */
    Paused,

    /** The user's turn has arrived — tapping opens the ticket flow. */
    YourTurn,

    /** Queue finished (sold out / closed); the user will not get in. */
    Closed,
}

/** Short badge label for a queue phase. Pure function — unit-tested directly. */
fun QueueListPhase.phaseLabel(): String =
    when (this) {
        QueueListPhase.QueueOpen -> "Antrian dibuka"
        QueueListPhase.Live -> "Berlangsung"
        QueueListPhase.Paused -> "Dijeda"
        QueueListPhase.YourTurn -> "Giliran Anda"
        QueueListPhase.Closed -> "Ditutup"
    }

/** Semantic color pair for a queue phase. Pure function — unit-tested directly. */
fun QueueListPhase.phaseColors(): StatusColorPair =
    when (this) {
        QueueListPhase.QueueOpen -> RusuhStatusColors.upcoming
        QueueListPhase.Live -> RusuhStatusColors.available
        QueueListPhase.Paused -> RusuhStatusColors.limited
        QueueListPhase.YourTurn -> RusuhStatusColors.available
        QueueListPhase.Closed -> RusuhStatusColors.soldOut
    }

/**
 * Display data for one My Queues row. UI-facing view model: every string is
 * already localized/formatted by the caller so the component stays clock- and
 * locale-agnostic (matches [QueuePositionUi] / [EventStatus]).
 *
 * @param eventId stable id used as the list key and passed to the tap callback.
 * @param title event name.
 * @param dateVenue pre-formatted "date · venue" line.
 * @param position the user's 1-based position in this queue. Must be >= 1.
 * @param nowServing pre-formatted now-serving line (e.g. "Melayani ke-120"); null
 *   hides the slot (e.g. before the queue starts admitting).
 * @param phase current queue phase, drives the badge label + color.
 * @param updatedAgo pre-formatted freshness line (e.g. "diperbarui 3 detik lalu").
 */
data class QueueListItemUi(
    val eventId: String,
    val title: String,
    val dateVenue: String,
    val position: Int,
    val nowServing: String?,
    val phase: QueueListPhase,
    val updatedAgo: String,
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(position >= 1) { "position must be >= 1, was $position" }
    }
}

/**
 * A single row on the My Queues (Antrian) screen: an event the user is queued
 * for, with its live position, now-serving line, a phase badge and a freshness
 * stamp. Tapping the row opens that event's Waiting Room.
 *
 * Stateless and token-driven — the caller supplies [data] and [onClick]; there
 * is NO ViewModel, NO data fetching here. Reuses the Waiting Room visual
 * language (position number as the emphasis, phase-accent color) so a row reads
 * as a compact Waiting Room card. The whole card is one semantic unit so screen
 * readers announce event + position + phase in a single pass.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueListItem(
    data: QueueListItemUi,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = data.phase.phaseColors()
    val description = buildQueueItemDescription(data)

    Card(
        onClick = { onClick(data.eventId) },
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = description },
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(RusuhSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterThumb()
            Spacer(Modifier.width(RusuhSpacing.xl))
            Column(modifier = Modifier.weight(1f)) {
                HeaderRow(title = data.title, phase = data.phase)
                Spacer(Modifier.height(RusuhSpacing.xs))
                Text(
                    text = data.dateVenue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(RusuhSpacing.md))
                PositionLine(data = data, accent = accent)
                data.nowServing?.let { serving ->
                    Spacer(Modifier.height(RusuhSpacing.xxs))
                    Text(
                        text = serving,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(RusuhSpacing.xs))
                Text(
                    text = data.updatedAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeaderRow(
    title: String,
    phase: QueueListPhase,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(RusuhSpacing.md))
        PhaseBadge(phase = phase)
    }
}

/** Phase pill. Mirrors [StatusBadge]'s shape/padding so the two read alike. */
@Composable
private fun PhaseBadge(phase: QueueListPhase) {
    val colors = phase.phaseColors()
    Surface(
        color = colors.container,
        contentColor = colors.content,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = phase.phaseLabel().uppercase(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = RusuhSpacing.md, vertical = RusuhSpacing.xxs),
        )
    }
}

/**
 * The position line: "Anda antrian ke-N" with N emphasised in the phase accent,
 * reusing the Waiting Room's positional copy. Closed queues show a terminal line
 * instead of a position (the number is meaningless once the queue is over).
 */
@Composable
private fun PositionLine(
    data: QueueListItemUi,
    accent: StatusColorPair,
) {
    when (data.phase) {
        QueueListPhase.Closed ->
            Text(
                text = "Antrian ditutup",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent.container,
            )

        QueueListPhase.YourTurn ->
            Text(
                text = "Giliran Anda — buka sekarang",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent.container,
            )

        else ->
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "Anda antrian ke-",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = data.position.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent.container,
                )
            }
    }
}

/**
 * Poster/thumbnail slot. A branded placeholder box — no image library is pulled
 * into commonMain for one slot (matches EventCard / DetailScreen PosterSlot).
 * M4 supplies the real poster from the queue payload.
 */
@Composable
private fun PosterThumb() {
    Box(
        modifier =
            Modifier
                .size(RusuhSpacing.queueThumb)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer),
    )
}

/** Full accessibility description for a row. Pure function — unit-tested directly. */
internal fun buildQueueItemDescription(data: QueueListItemUi): String =
    buildString {
        append("${data.title}. ")
        append("${data.dateVenue}. ")
        when (data.phase) {
            QueueListPhase.Closed -> append("Antrian ditutup. ")
            QueueListPhase.YourTurn -> append("Giliran Anda. ")
            else -> append("Anda antrian ke-${data.position}. ")
        }
        append("${data.phase.phaseLabel()}. ")
        append(data.updatedAgo)
        append(".")
    }
