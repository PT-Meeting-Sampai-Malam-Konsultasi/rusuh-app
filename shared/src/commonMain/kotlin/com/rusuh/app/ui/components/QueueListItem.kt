package com.rusuh.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.rusuh.app.ui.theme.RusuhBrandColors
import com.rusuh.app.ui.theme.RusuhBrandMetrics
import com.rusuh.app.ui.theme.RusuhShapes
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.queue_list_item_closed_headline
import rusuh_app.shared.generated.resources.queue_list_item_description
import rusuh_app.shared.generated.resources.queue_list_item_open_action
import rusuh_app.shared.generated.resources.queue_list_item_position_lead
import rusuh_app.shared.generated.resources.queue_list_item_your_turn_headline
import rusuh_app.shared.generated.resources.queue_position_eta_minutes

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
    /** Queue announced but not yet admitting; positions not moving yet. */
    QueueOpen,

    /** Queue is actively admitting; the line is moving. */
    Live,

    /** Temporarily halted by the organiser; positions frozen. */
    Paused,

    /** The user's turn has arrived; tapping opens the ticket flow. */
    YourTurn,

    /** Queue finished (sold out / closed); the user will not get in. */
    Closed,
}

/**
 * Display data for one My Queues row. Event facts (title, date/venue) arrive
 * ready to show; live queue numbers arrive raw so the row can localize them.
 *
 * @param eventId stable id used as the list key and passed to the tap callback.
 * @param title event name.
 * @param dateVenue pre-formatted "date, venue" line.
 * @param position the user's place in this queue (seq - served). Must be >= 1 while
 *   waiting (open / live / paused); a [QueueListPhase.YourTurn] or
 *   [QueueListPhase.Closed] row may legitimately be 0 or negative, and its number
 *   is never shown.
 * @param nowServing the number currently being served; null hides the slot
 *   (e.g. before the queue starts admitting).
 * @param phase current queue phase, drives the stamp label + color.
 * @param etaMinutes estimated minutes until the user's turn; null = unknown. Only
 *   shown on [QueueListPhase.Live] rows. Must be >= 0 when set.
 * @param updatedAgoSeconds seconds since the status was last refreshed. Must be >= 0.
 */
@Immutable
data class QueueListItemUi(
    val eventId: String,
    val title: String,
    val dateVenue: String,
    val position: Int,
    val nowServing: Int?,
    val phase: QueueListPhase,
    val updatedAgoSeconds: Int,
    val etaMinutes: Int? = null,
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(!phase.showsPosition() || position >= 1) { "position must be >= 1 while waiting, was $position" }
        require(etaMinutes == null || etaMinutes >= 0) { "etaMinutes must be >= 0, was $etaMinutes" }
        require(updatedAgoSeconds >= 0) { "updatedAgoSeconds must be >= 0, was $updatedAgoSeconds" }
    }
}

/**
 * A single row on the My Queues (Antrian) screen: an event the user is queued
 * for, with its live position, now-serving line, a phase stamp and a freshness
 * stamp. Tapping the row opens that event's Waiting Room.
 *
 * Styled after the Waiting Room / Home cards: paper card, 2.5dp ink outline and
 * a hard offset ink shadow, a stamp for the phase, and the position
 * number in the display face as the emphasis. Stateless and token-driven; the
 * caller supplies [data] and [onClick]. The whole card is one semantic unit so
 * screen readers announce event + position + phase in a single pass.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueListItem(
    data: QueueListItemUi,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = buildQueueItemDescription(data)
    val shadow = RusuhBrandMetrics.shadowOffset

    // Room on the end/bottom edges so the hard shadow is not clipped by the list.
    Box(modifier = modifier.fillMaxWidth().padding(end = shadow, bottom = shadow)) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .offset(x = shadow, y = shadow)
                    .background(RusuhBrandColors.Ink, RusuhShapes.small),
        )
        Surface(
            onClick = { onClick(data.eventId) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { contentDescription = description },
            shape = RusuhShapes.small,
            color = RusuhBrandColors.Card,
            contentColor = RusuhBrandColors.Ink,
            border = BorderStroke(RusuhBrandMetrics.borderWidth, RusuhBrandColors.Ink),
        ) {
            Column(modifier = Modifier.padding(RusuhSpacing.xl)) {
                StampRow(data = data)
                Spacer(Modifier.height(RusuhSpacing.lg))
                EventRow(data = data)
                Spacer(Modifier.height(RusuhSpacing.lg))
                HorizontalDivider(color = RusuhBrandColors.Hair)
                Spacer(Modifier.height(RusuhSpacing.lg))
                StatusRow(data = data)
            }
        }
    }
}

@Composable
private fun StampRow(data: QueueListItemUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhaseStamp(phase = data.phase)
        Spacer(Modifier.width(RusuhSpacing.md))
        Text(
            text = updatedAgoText(data.updatedAgoSeconds),
            style = MaterialTheme.typography.bodySmall,
            color = RusuhBrandColors.Ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Phase stamp: uppercase, a flat block like the hi-fi `.stamp`. */
@Composable
private fun PhaseStamp(phase: QueueListPhase) {
    val colors = phase.phaseColors()
    Surface(
        color = colors.container,
        contentColor = colors.content,
        shape = RusuhShapes.small,
    ) {
        Text(
            text = stringResource(phase.phaseLabel()).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = RusuhSpacing.lg, vertical = RusuhSpacing.xs),
        )
    }
}

@Composable
private fun EventRow(data: QueueListItemUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PosterThumb(initial = data.title.firstOrNull()?.uppercaseChar()?.toString().orEmpty())
        Spacer(Modifier.width(RusuhSpacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = data.title,
                style = MaterialTheme.typography.headlineMedium,
                color = RusuhBrandColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(RusuhSpacing.xxs))
            Text(
                text = data.dateVenue,
                style = MaterialTheme.typography.bodyMedium,
                color = RusuhBrandColors.Ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Position block (left) and context line + open action (right).
 * Closed queues show a terminal headline instead of a number (the number is
 * meaningless once the queue is over); your-turn shows the Waiting Room's
 * "Giliran Anda!" headline.
 */
@Composable
private fun StatusRow(data: QueueListItemUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            when (data.phase) {
                QueueListPhase.Closed ->
                    HeadlineText(
                        stringResource(Res.string.queue_list_item_closed_headline),
                        RusuhBrandColors.Ink,
                    )
                QueueListPhase.YourTurn ->
                    HeadlineText(
                        stringResource(Res.string.queue_list_item_your_turn_headline),
                        RusuhBrandColors.PinkInk,
                    )
                else -> {
                    Text(
                        text = stringResource(Res.string.queue_list_item_position_lead),
                        style = MaterialTheme.typography.bodySmall,
                        color = RusuhBrandColors.Ink2,
                    )
                    Text(
                        text = formatQueueNumber(data.position),
                        style = MaterialTheme.typography.headlineLarge,
                        color = RusuhBrandColors.PinkInk,
                        maxLines = 1,
                    )
                    visibleEtaMinutes(data)?.let { minutes ->
                        Text(
                            text = stringResource(Res.string.queue_position_eta_minutes, minutes),
                            style = MaterialTheme.typography.bodySmall,
                            color = RusuhBrandColors.Ink2,
                        )
                    }
                }
            }
            Spacer(Modifier.height(RusuhSpacing.xxs))
            Text(
                text = contextLine(data),
                style = MaterialTheme.typography.bodySmall,
                color = RusuhBrandColors.Ink2,
            )
        }
        Spacer(Modifier.width(RusuhSpacing.md))
        Text(
            text = "${stringResource(Res.string.queue_list_item_open_action)} →",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
            color = RusuhBrandColors.Ink,
        )
    }
}

@Composable
private fun HeadlineText(
    text: String,
    color: Color,
) {
    Text(text = text, style = MaterialTheme.typography.headlineMedium, color = color)
}

/**
 * Poster/thumbnail slot. A branded placeholder block (pink tint, ink outline,
 * the event initial); no image library is pulled into commonMain for one slot
 * (matches EventCard / DetailScreen PosterSlot). M4 supplies the real poster.
 */
@Composable
private fun PosterThumb(initial: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(RusuhSpacing.queueThumb)
                .clip(RusuhShapes.small)
                .background(RusuhBrandColors.PinkTint),
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.headlineMedium,
            color = RusuhBrandColors.PinkInk,
        )
    }
}

@Composable
private fun updatedAgoText(seconds: Int): String =
    updatedAgoLabel(seconds).let { stringResource(it.resource, it.count) }

@Composable
private fun contextLine(data: QueueListItemUi): String {
    val resource = contextLineResource(data) ?: return ""
    return if (data.phase == QueueListPhase.Live) {
        stringResource(resource, formatQueueNumber(data.nowServing ?: 0))
    } else {
        stringResource(resource)
    }
}

/** Full accessibility description for a row (event, venue, position/state, phase, freshness). */
@Composable
internal fun buildQueueItemDescription(data: QueueListItemUi): String {
    val etaText = visibleEtaMinutes(data)?.let { stringResource(Res.string.queue_position_eta_minutes, it) }
    val state =
        when (data.phase) {
            QueueListPhase.Closed -> stringResource(Res.string.queue_list_item_closed_headline)
            QueueListPhase.YourTurn -> stringResource(Res.string.queue_list_item_your_turn_headline)
            else ->
                joinLeadAndNumber(
                    stringResource(Res.string.queue_list_item_position_lead),
                    formatQueueNumber(data.position),
                ) + etaText?.let { ", $it" }.orEmpty()
        }
    return stringResource(
        Res.string.queue_list_item_description,
        data.title,
        data.dateVenue,
        state,
        stringResource(data.phase.phaseLabel()),
        updatedAgoText(data.updatedAgoSeconds),
    )
}
