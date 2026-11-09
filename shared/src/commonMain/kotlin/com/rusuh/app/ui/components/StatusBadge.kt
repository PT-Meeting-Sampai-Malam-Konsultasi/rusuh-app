package com.rusuh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rusuh.app.ui.theme.RusuhShapes
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.status_prefix

private val STAMP_FONT_SIZE = 11.sp
private val STAMP_LETTER_SPACING = 0.55.sp
private val STAMP_DASH = 4.dp

/**
 * A "stamp" showing event availability: a slightly rotated, square-cornered tag
 * with a 2dp outline. Available fills pink and shows a leading dot; temporarily
 * full is dashed; sold out is solid ink; coming soon is blue-on-card.
 *
 * The badge as a whole carries a single content description so screen readers
 * announce the status once rather than reading the dot and label separately.
 */
@Composable
fun StatusBadge(
    status: EventStatus,
    modifier: Modifier = Modifier,
) {
    val colors = status.badgeColors()
    val label = stringResource(status.badgeLabel())
    val statusDescription = stringResource(Res.string.status_prefix, label)
    // Filled stamps outline in their own fill; card-colored ones outline in the text color.
    val filled = status is EventStatus.Available || status is EventStatus.SoldOut
    val outline = if (filled) colors.container else colors.content
    val dashed = status is EventStatus.TemporarilyFull
    val shapeModifier =
        if (dashed) {
            Modifier.dashedBorder(outline)
        } else {
            Modifier.border(RusuhSpacing.stampBorder, outline, RusuhShapes.square)
        }
    Row(
        modifier =
            modifier
                .clearAndSetSemantics { contentDescription = statusDescription }
                .rotate(RusuhSpacing.STAMP_ROTATION)
                .background(colors.container, RusuhShapes.square)
                .then(shapeModifier)
                .padding(horizontal = RusuhSpacing.md, vertical = RusuhSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (status is EventStatus.Available) {
            StatusDot(color = colors.content)
            Spacer(Modifier.width(RusuhSpacing.sm))
        }
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontSize = STAMP_FONT_SIZE,
            letterSpacing = STAMP_LETTER_SPACING,
            fontWeight = FontWeight.Bold,
            color = colors.content,
            maxLines = 1,
        )
    }
}

/** Dashed rectangular outline, drawn inside the bounds like a CSS `border: dashed`. */
private fun Modifier.dashedBorder(color: Color): Modifier =
    drawBehind {
        val stroke = RusuhSpacing.stampBorder.toPx()
        val dash = STAMP_DASH.toPx()
        drawRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
        )
    }

@Composable
private fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier =
            modifier
                .size(RusuhSpacing.statusDot)
                .background(color = color, shape = CircleShape),
    )
}
