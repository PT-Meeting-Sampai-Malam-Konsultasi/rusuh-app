package com.rusuh.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.rusuh.app.ui.theme.RusuhBrandColors
import com.rusuh.app.ui.theme.RusuhBrandMetrics
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhShapes
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.ticket_order_done_badge
import rusuh_app.shared.generated.resources.ticket_order_paid_badge
import rusuh_app.shared.generated.resources.ticket_order_paid_description
import rusuh_app.shared.generated.resources.ticket_order_summary
import rusuh_app.shared.generated.resources.ticket_order_view_eticket
import rusuh_app.shared.generated.resources.ticket_order_view_eticket_for
import rusuh_app.shared.generated.resources.ticket_order_view_eticket_primary

/** Poster artwork stand-in until real event images are wired (M4). */
enum class TicketPoster { Pink, Blue, Grey }

/** One attendee chip on an order card: ticket category plus the holder's name. */
@Immutable
data class TicketAttendeeUi(
    val category: String,
    val holderName: String,
)

/**
 * Display data for one purchased order on the Tiket tab. Everything arrives
 * pre-formatted (dates, money, countdown) so the card stays locale-agnostic.
 *
 * @param isPast true for a finished event: muted card, "SELESAI" badge and an
 *   outlined (non-primary) action.
 * @param countdownLabel e.g. "25 hari lagi"; null hides it (past orders).
 */
@Immutable
data class TicketOrderUi(
    val orderId: String,
    val eventTitle: String,
    val dateVenue: String,
    val posterLabel: String,
    val poster: TicketPoster,
    val attendees: List<TicketAttendeeUi>,
    val totalLabel: String,
    val dayLabel: String,
    val monthLabel: String,
    val countdownLabel: String? = null,
    val isPast: Boolean = false,
)

/**
 * A purchased-order card on the Tiket tab (Figma "Event Purchased"): poster
 * thumbnail, paid badge, event facts, attendee chips, order number and total, a
 * big date block and the "Lihat e-tiket" action. Paper card with a thick ink
 * outline and a hard ink shadow, like the other list cards. Stateless; the whole
 * card is not clickable, only the action is, so the order facts stay readable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TicketOrderCard(
    data: TicketOrderUi,
    onViewETicket: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shadow = RusuhBrandMetrics.shadowOffset
    val muted = data.isPast
    Box(modifier = modifier.fillMaxWidth().padding(end = shadow, bottom = shadow)) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .offset(x = shadow, y = shadow)
                    .background(RusuhBrandColors.Ink, RusuhShapes.small),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(RusuhBrandColors.Card, RusuhShapes.small)
                    .border(RusuhBrandMetrics.borderWidth, RusuhBrandColors.Ink, RusuhShapes.small)
                    .padding(RusuhSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.lg)) {
                PosterThumb(data.posterLabel, data.poster, muted)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(RusuhSpacing.xs),
                ) {
                    StatusBadge(muted)
                    Text(
                        text = data.eventTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = RusuhBrandColors.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = data.dateVenue,
                        style = MaterialTheme.typography.bodySmall,
                        color = RusuhBrandColors.Ink2,
                    )
                }
            }
            if (data.attendees.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
                ) {
                    data.attendees.forEach { AttendeeChip(it) }
                }
            }
            Text(
                text = stringResource(Res.string.ticket_order_summary, data.orderId, data.totalLabel),
                style = MaterialTheme.typography.bodySmall,
                color = RusuhBrandColors.Ink3,
            )
            Box(Modifier.fillMaxWidth().height(RusuhSpacing.hairline).background(RusuhBrandColors.Hair))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
            ) {
                DateBlock(data, muted)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    ViewETicketButton(
                        eventTitle = data.eventTitle,
                        onClick = { onViewETicket(data.orderId) },
                        primary = !muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterThumb(
    label: String,
    poster: TicketPoster,
    muted: Boolean,
) {
    val color =
        when (poster) {
            TicketPoster.Pink -> RusuhBrandColors.Pink
            TicketPoster.Blue -> RusuhColors.Blue
            TicketPoster.Grey -> RusuhBrandColors.Ink2
        }
    Box(
        modifier =
            Modifier
                .width(RusuhSpacing.queueThumb)
                .height(RusuhSpacing.posterHeight + RusuhSpacing.xl)
                .clearAndSetSemantics {}
                .clip(RusuhShapes.small)
                .background(if (muted) color.copy(alpha = POSTER_MUTED_ALPHA) else color),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = POSTER_LABEL_SIZE,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.padding(RusuhSpacing.sm),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun StatusBadge(muted: Boolean) {
    val text =
        stringResource(if (muted) Res.string.ticket_order_done_badge else Res.string.ticket_order_paid_badge)
    val plain =
        stringResource(if (muted) Res.string.ticket_order_done_badge else Res.string.ticket_order_paid_description)
    val color = if (muted) RusuhBrandColors.Ink2 else RusuhColors.BlueInk
    Text(
        text = if (muted) text else "✓ $text",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier =
            Modifier
                .semantics { contentDescription = plain }
                .border(RusuhSpacing.borderThin, color, RusuhShapes.small)
                .padding(horizontal = RusuhSpacing.md, vertical = RusuhSpacing.xxs),
    )
}

@Composable
private fun AttendeeChip(attendee: TicketAttendeeUi) {
    Column(
        modifier =
            Modifier
                .semantics(mergeDescendants = true) {}
                .border(RusuhSpacing.borderThin, RusuhBrandColors.Ink3, RusuhShapes.small)
                .padding(horizontal = RusuhSpacing.md, vertical = RusuhSpacing.xs),
    ) {
        Text(
            text = attendee.category.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = RusuhColors.BlueInk,
        )
        Text(
            text = attendee.holderName,
            style = MaterialTheme.typography.bodySmall,
            color = RusuhBrandColors.Ink,
        )
    }
}

@Composable
private fun DateBlock(
    data: TicketOrderUi,
    muted: Boolean,
) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = data.dayLabel,
            style = MaterialTheme.typography.headlineLarge,
            color = if (muted) RusuhBrandColors.Ink2 else RusuhBrandColors.Ink,
        )
        Column {
            Text(
                text = data.monthLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = RusuhBrandColors.Ink,
            )
            data.countdownLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = RusuhBrandColors.PinkInk,
                )
            }
        }
    }
}

@Composable
private fun ViewETicketButton(
    eventTitle: String,
    onClick: () -> Unit,
    primary: Boolean,
) {
    val label =
        stringResource(
            if (primary) Res.string.ticket_order_view_eticket_primary else Res.string.ticket_order_view_eticket,
        )
    val description = stringResource(Res.string.ticket_order_view_eticket_for, eventTitle)
    val modifier =
        Modifier
            .heightIn(min = RusuhSpacing.actionMinHeight)
            .semantics { contentDescription = description }
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RusuhShapes.small,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = RusuhBrandColors.Ink,
                    contentColor = RusuhBrandColors.Paper,
                ),
        ) { Text(label, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RusuhShapes.small,
            border = BorderStroke(RusuhBrandMetrics.borderWidth, RusuhBrandColors.Ink),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RusuhBrandColors.Ink),
        ) { Text(label, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
    }
}

private const val POSTER_MUTED_ALPHA = 0.7f
private val POSTER_LABEL_SIZE = 10.sp
