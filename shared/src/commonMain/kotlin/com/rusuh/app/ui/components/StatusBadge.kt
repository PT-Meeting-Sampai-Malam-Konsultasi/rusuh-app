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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.StatusColorPair
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.status_prefix

/** Hi-fi tilts every status stamp slightly, like a rubber stamp. */
private const val STAMP_ROTATION_DEGREES = -2f

private const val DASH_ON = 6f
private const val DASH_OFF = 4f

/**
 * Status "stamp" for an event (Figma: word first, colour second).
 *
 * Square-cornered, bordered and slightly rotated; "Penuh sementara" uses a dashed
 * border and the waiting-room state carries a leading dot. The stamp as a whole
 * carries a single content description so screen readers announce the status once
 * rather than reading the dot and label separately.
 */
@Composable
fun StatusBadge(
    status: EventStatus,
    modifier: Modifier = Modifier,
) {
    val colors = status.badgeColors()
    val label = stringResource(status.badgeLabel())
    val statusDescription = stringResource(Res.string.status_prefix, label)
    Row(
        modifier =
            modifier
                .clearAndSetSemantics { contentDescription = statusDescription }
                .graphicsLayer { rotationZ = STAMP_ROTATION_DEGREES }
                .stampBackground(colors)
                .padding(horizontal = RusuhSpacing.md, vertical = RusuhSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (status is EventStatus.WaitingRoomOpen) {
            StatusDot(color = colors.content)
            Spacer(Modifier.width(RusuhSpacing.sm))
        }
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.content,
        )
    }
}

/** Solid or dashed bordered rectangle behind the stamp text. */
private fun Modifier.stampBackground(colors: StatusColorPair): Modifier =
    if (colors.dashedBorder) {
        this
            .background(colors.container, RectangleShape)
            .drawBehind {
                val stroke = RusuhSpacing.stampBorder.toPx()
                drawRect(
                    color = colors.border,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style =
                        Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF)),
                        ),
                )
            }
    } else {
        this
            .background(colors.container, RectangleShape)
            .border(RusuhSpacing.stampBorder, colors.border, RectangleShape)
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
