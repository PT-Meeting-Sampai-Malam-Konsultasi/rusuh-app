package com.rusuh.app.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhShapes
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.status_prefix

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
    val label = stringResource(status.badgeLabel())
    val statusDescription = stringResource(Res.string.status_prefix, label)
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = statusDescription },
        color = colors.container,
        contentColor = colors.content,
        shape = RusuhShapes.pill,
        border = BorderStroke(RusuhSpacing.inkBorder, RusuhColors.Ink),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = RusuhSpacing.lg, vertical = RusuhSpacing.xs),
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
                fontWeight = FontWeight.Bold,
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
                .size(RusuhSpacing.statusDot)
                .background(color = color, shape = CircleShape),
    )
}
