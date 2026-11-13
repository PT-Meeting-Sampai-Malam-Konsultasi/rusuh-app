@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.waitingroom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.RusuhTextStyles
import com.rusuh.app.ui.theme.rusuhDisplayFontFamily
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.waiting_guest_banner
import rusuh_app.shared.generated.resources.waiting_guest_sign_in
import rusuh_app.shared.generated.resources.waiting_poster_sold_out_stamp

/**
 * Event poster: blue halftone card with a hard ink shadow (grey with a "HABIS" stamp once sold out).
 * The live/prequeue/paused/closed tag above the title follows [phase].
 */
@Composable
internal fun EventPoster(
    event: WaitingRoomEventUi,
    phase: WaitingRoomPhase,
) {
    val soldOut = phase == WaitingRoomPhase.SoldOut
    val base = if (soldOut) RusuhColors.InkSubtle else RusuhColors.Blue
    val dots = if (soldOut) RusuhColors.InkMuted else RusuhColors.Pink
    val tag = phase.posterTagText()
    val stampText = stringResource(Res.string.waiting_poster_sold_out_stamp)
    Box(modifier = Modifier.fillMaxWidth().padding(end = RusuhSpacing.hardShadow, bottom = RusuhSpacing.hardShadow)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = RusuhSpacing.hardShadow, y = RusuhSpacing.hardShadow)
                .background(RusuhColors.Ink),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(base)
                    .drawBehind { drawHalftone(dots) }
                    .padding(horizontal = RusuhSpacing.xl, vertical = RusuhSpacing.lg),
        ) {
            Text(text = tag, style = MaterialTheme.typography.bodySmall, color = RusuhColors.Paper)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = event.title,
                    style =
                        RusuhTextStyles.etaHeadline.copy(
                            fontFamily = rusuhDisplayFontFamily(),
                            fontSize = POSTER_TITLE_SIZE,
                        ),
                    color = RusuhColors.Paper,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = event.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = RusuhColors.PinkInk,
                    modifier =
                        Modifier.rotate(
                            SUBTITLE_TILT,
                        ).background(
                            RusuhColors.Paper,
                        ).padding(horizontal = RusuhSpacing.sm, vertical = RusuhSpacing.xxs),
                )
            }
            Spacer(Modifier.height(RusuhSpacing.md))
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(RusuhColors.Paper))
            Spacer(Modifier.height(RusuhSpacing.md))
            Text(text = event.dateTimeLine, style = MaterialTheme.typography.bodySmall, color = RusuhColors.Paper)
            Text(text = event.venueLine, style = MaterialTheme.typography.bodySmall, color = RusuhColors.Paper)
        }
        if (soldOut) {
            Text(
                text = stampText,
                style = RusuhTextStyles.etaHeadline.copy(fontFamily = rusuhDisplayFontFamily()),
                color = RusuhColors.Ink,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .rotate(STAMP_TILT)
                        .background(RusuhColors.Paper)
                        .border(RusuhSpacing.borderBold, RusuhColors.Ink)
                        .padding(horizontal = RusuhSpacing.lg, vertical = RusuhSpacing.xs),
            )
        }
    }
}

internal fun DrawScope.drawHalftone(color: Color) {
    val step = HALFTONE_STEP.dp.toPx()
    val radius = HALFTONE_RADIUS.dp.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(color = color.copy(alpha = HALFTONE_ALPHA), radius = radius, center = Offset(x, y))
            x += step
        }
        y += step
    }
}

@Composable
internal fun GuestBanner(
    onSignIn: () -> Unit,
    prominent: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(RusuhSpacing.borderBold, RusuhColors.Ink)
                .background(if (prominent) RusuhColors.Pink else RusuhColors.PaperDeep)
                .padding(horizontal = RusuhSpacing.lg, vertical = RusuhSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
    ) {
        Text(
            text = stringResource(Res.string.waiting_guest_banner),
            style = MaterialTheme.typography.bodyMedium,
            color = RusuhColors.Ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(Res.string.waiting_guest_sign_in),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = RusuhColors.Paper,
            modifier =
                Modifier
                    .background(RusuhColors.Ink)
                    .clickable(role = Role.Button, onClick = onSignIn)
                    .padding(horizontal = RusuhSpacing.xl, vertical = RusuhSpacing.md),
        )
    }
}

private const val STAMP_TILT = -14f
private const val SUBTITLE_TILT = -2f
private const val HALFTONE_STEP = 8
private const val HALFTONE_RADIUS = 1.5f
private const val HALFTONE_ALPHA = 0.55f
private val POSTER_TITLE_SIZE = 40.sp

/** Resolves the poster tag, filling the opening time for the pre-queue tag. */
@Composable
private fun WaitingRoomPhase.posterTagText(): String =
    if (this is WaitingRoomPhase.PreQueue) {
        stringResource(posterTag(), opensAtLabel)
    } else {
        stringResource(posterTag())
    }
