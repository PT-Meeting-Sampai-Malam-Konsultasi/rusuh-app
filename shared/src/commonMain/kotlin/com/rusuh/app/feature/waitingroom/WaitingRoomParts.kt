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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.rusuh.app.ui.components.formatCountdown
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.RusuhTextStyles
import com.rusuh.app.ui.theme.rusuhDisplayFontFamily
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.waiting_leave
import rusuh_app.shared.generated.resources.waiting_note_app
import rusuh_app.shared.generated.resources.waiting_note_nowait_body
import rusuh_app.shared.generated.resources.waiting_note_nowait_title
import rusuh_app.shared.generated.resources.waiting_note_safe_body
import rusuh_app.shared.generated.resources.waiting_note_safe_title
import rusuh_app.shared.generated.resources.waiting_note_turn_body
import rusuh_app.shared.generated.resources.waiting_note_turn_title
import rusuh_app.shared.generated.resources.waiting_turn_timer_label

@Composable
internal fun Lead(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = RusuhColors.Ink)
}

@Composable
internal fun Body(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = RusuhColors.Ink)
}

@Composable
internal fun Muted(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = RusuhColors.InkMuted)
}

internal enum class HeadlineSize { Large, Medium }

@Composable
internal fun Headline(
    text: String,
    size: HeadlineSize = HeadlineSize.Large,
    color: Color = RusuhColors.Ink,
    shadow: Color? = null,
) {
    val style =
        when (size) {
            HeadlineSize.Large -> RusuhTextStyles.displayHeadline
            HeadlineSize.Medium -> RusuhTextStyles.etaHeadline
        }.copy(fontFamily = rusuhDisplayFontFamily())
    Box(Modifier.semantics { heading() }) {
        if (shadow != null) {
            Text(
                text = text,
                style = style,
                color = shadow,
                modifier = Modifier.offset(x = RusuhSpacing.xs, y = RusuhSpacing.xxs),
            )
        }
        Text(text = text, style = style, color = color)
    }
}

/** Ink button with the hard pink offset shadow (Figma primary CTA). */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(end = RusuhSpacing.hardShadow, bottom = RusuhSpacing.hardShadow)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = RusuhSpacing.hardShadow, y = RusuhSpacing.hardShadow)
                .background(RusuhColors.Pink),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = RusuhSpacing.primaryButtonHeight)
                    .background(RusuhColors.Ink)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$text $FORWARD_ARROW",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = rusuhDisplayFontFamily()),
                fontWeight = FontWeight.Bold,
                color = if (enabled) RusuhColors.Paper else RusuhColors.PaperDeep,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun TurnTimer(seconds: Int) {
    val label = stringResource(Res.string.waiting_turn_timer_label)
    val time = formatCountdown(seconds)
    Row(
        modifier =
            Modifier
                .semantics(mergeDescendants = true) { contentDescription = "$time $label" }
                .border(RusuhSpacing.borderBold, RusuhColors.Ink)
                .background(RusuhColors.Paper)
                .padding(horizontal = RusuhSpacing.lg, vertical = RusuhSpacing.md),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
    ) {
        Text(
            text = time,
            style = RusuhTextStyles.etaHeadline.copy(fontFamily = rusuhDisplayFontFamily()),
            color = if (seconds <= 10) RusuhColors.PinkInk else RusuhColors.Ink,
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = RusuhColors.InkMuted)
    }
}

@Composable
internal fun TextLink(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = RusuhColors.Ink,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = RusuhSpacing.md),
    )
}

@Composable
internal fun WaitingNotes(onLeave: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderBold).background(RusuhColors.Ink))
        Note(Res.string.waiting_note_safe_title, Res.string.waiting_note_safe_body)
        Divider()
        Note(Res.string.waiting_note_turn_title, Res.string.waiting_note_turn_body)
        Divider()
        Note(Res.string.waiting_note_nowait_title, Res.string.waiting_note_nowait_body)
        Divider()
        Spacer(Modifier.height(RusuhSpacing.lg))
        Muted(stringResource(Res.string.waiting_note_app))
        TextLink(text = stringResource(Res.string.waiting_leave), onClick = onLeave)
    }
}

@Composable
internal fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(RusuhColors.Hair))
}

@Composable
internal fun Note(
    title: StringResource,
    body: StringResource,
) {
    Column(Modifier.padding(vertical = RusuhSpacing.lg)) {
        Text(
            text = stringResource(title),
            style = RusuhTextStyles.queueSectionTitle.copy(fontFamily = rusuhDisplayFontFamily()),
            color = RusuhColors.Ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(RusuhSpacing.xs))
        Muted(stringResource(body))
    }
}

private const val FORWARD_ARROW = "→"
