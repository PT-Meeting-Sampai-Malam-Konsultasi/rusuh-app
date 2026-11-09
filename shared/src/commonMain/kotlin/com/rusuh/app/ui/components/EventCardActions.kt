package com.rusuh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.event_card_action_buy
import rusuh_app.shared.generated.resources.event_card_action_join_queue
import rusuh_app.shared.generated.resources.event_card_action_queue_anyway
import rusuh_app.shared.generated.resources.event_card_action_remind
import rusuh_app.shared.generated.resources.event_card_footer_ended
import rusuh_app.shared.generated.resources.event_card_footer_opens
import rusuh_app.shared.generated.resources.event_footer_sold_out
import rusuh_app.shared.generated.resources.event_footer_temporarily_full
import rusuh_app.shared.generated.resources.status_passthrough

/** Hard-shadow button; primary is solid ink, otherwise a paper-card ghost. */
@Composable
internal fun ActionButton(
    label: String,
    primary: Boolean,
    onClick: (() -> Unit)?,
) {
    val container = if (primary) RusuhColors.Ink else RusuhColors.PaperCard
    val content = if (primary) RusuhColors.Paper else RusuhColors.Ink
    val shadow = if (primary) RusuhColors.Pink else RusuhColors.Ink
    val offset = RusuhSpacing.buttonShadow
    Box(modifier = Modifier.padding(end = offset, bottom = offset)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = RusuhSpacing.actionMinHeight)
                    .drawBehind {
                        val o = offset.toPx()
                        drawRect(color = shadow, topLeft = Offset(o, o), size = size)
                    }.background(container, RectangleShape)
                    .border(RusuhSpacing.ruleThick, RusuhColors.Ink, RectangleShape)
                    .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                    .padding(horizontal = RusuhSpacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = content,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Call-to-action shown for a status: its label and whether it is the solid (primary) style. */
internal data class CardAction(
    val label: StringResource,
    val primary: Boolean,
)

/** Pure status -> call-to-action mapping; null when the status offers none. Unit-tested. */
internal fun EventStatus.cardAction(): CardAction? =
    when (this) {
        EventStatus.WaitingRoomOpen -> CardAction(Res.string.event_card_action_join_queue, primary = true)
        EventStatus.OnSale, EventStatus.AlmostGone -> CardAction(Res.string.event_card_action_buy, primary = true)
        EventStatus.TemporarilyFull -> CardAction(Res.string.event_card_action_queue_anyway, primary = false)
        is EventStatus.ComingSoon -> CardAction(Res.string.event_card_action_remind, primary = false)
        is EventStatus.SoldOut, is EventStatus.Ended -> null
    }

/**
 * Localized supporting footer lines for a status (opens-at + countdown, sold-out
 * stats, ended note), empty when the status needs none. `@Composable` so it
 * resolves string resources at the call site; the key/arg mapping itself is
 * covered by the pure [footerSpecs].
 */
@Composable
fun EventStatus.footerTexts(): List<String> =
    footerSpecs().map { spec ->
        // Resolve by arg count rather than a spread (avoids the array copy detekt
        // flags). Footer resources take 0, 1 or 2 args — enumerate them.
        when (spec.args.size) {
            0 -> stringResource(spec.resource)
            1 -> stringResource(spec.resource, spec.args[0])
            else -> stringResource(spec.resource, spec.args[0], spec.args[1])
        }
    }

/**
 * A status's footer lines as string resource + format args (empty when the
 * status needs none). Pure function — unit-tested directly; the composable
 * [footerTexts] resolves it. Pre-formatted caller values (countdown) go through a
 * passthrough resource, so no localization is lost.
 */
internal fun EventStatus.footerSpecs(): List<FooterSpec> =
    when (this) {
        EventStatus.WaitingRoomOpen, EventStatus.OnSale, EventStatus.AlmostGone -> emptyList()
        EventStatus.TemporarilyFull -> listOf(FooterSpec(Res.string.event_footer_temporarily_full))
        is EventStatus.SoldOut ->
            listOf(FooterSpec(Res.string.event_footer_sold_out, listOf(capacity.formatThousands(), soldOutInMinutes)))
        is EventStatus.ComingSoon ->
            listOfNotNull(
                opensAt?.let { FooterSpec(Res.string.event_card_footer_opens, listOf(it)) },
                FooterSpec(Res.string.status_passthrough, listOf(countdown)),
            )
        is EventStatus.Ended -> listOf(FooterSpec(Res.string.event_card_footer_ended, listOf(closedOn)))
    }

/** Resource + format args for a status footer line. */
internal data class FooterSpec(
    val resource: StringResource,
    val args: List<Any> = emptyList(),
)
