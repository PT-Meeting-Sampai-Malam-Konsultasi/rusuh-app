package com.rusuh.app.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.rusuh.app.ui.components.BrutalButton
import com.rusuh.app.ui.components.brutalBox
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import com.rusuh.app.ui.theme.display

// Small building blocks shared by the five sign-in steps (SignInContent) and the full wrapper.

/** Step title (display face), a heading for TalkBack; announced politely when the text changes. */
@Composable
internal fun SignInHeadline(text: String) {
    Text(
        text = text,
        style = RusuhBrandTextStyles.sheetHeadline.display(),
        color = RusuhColors.BrandInk,
        modifier =
            Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
    )
}

/** Muted lead text under the title; [emphasis] parts (an email, for instance) render bold in ink. */
@Composable
internal fun SignInSubtitle(
    text: String,
    emphasis: List<String> = emptyList(),
) {
    Text(
        text = styledParts(AnnotatedString(text), emphasis, EMPHASIS_STYLE),
        style = RusuhBrandTextStyles.bodyLarge.body(),
        color = RusuhColors.BrandMuted,
    )
}

/** Visible field label ("Email", "Nama", "Kode 6 digit"). The field itself carries the accessible name. */
@Composable
internal fun FieldLabel(text: String) {
    Text(
        text = text,
        style = RusuhBrandTextStyles.label.body(),
        color = RusuhColors.BrandInk,
    )
}

/** Small supporting line; [emphasis] parts render bold in ink, [underline] parts underlined (inert links). */
@Composable
@Suppress("LongParameterList")
internal fun HintText(
    text: String,
    modifier: Modifier = Modifier,
    emphasis: List<String> = emptyList(),
    underline: List<String> = emptyList(),
    links: Map<String, String> = emptyMap(),
    textAlign: TextAlign = TextAlign.Start,
) {
    val emphasised = styledParts(AnnotatedString(text), emphasis, EMPHASIS_STYLE)
    val underlined = styledParts(emphasised, underline, SpanStyle(textDecoration = TextDecoration.Underline))
    Text(
        text = withLinks(underlined, links),
        style = RusuhBrandTextStyles.note.body(),
        color = RusuhColors.BrandMuted,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** Makes each key of [links] in [text] open its URL in the browser. */
private fun withLinks(
    text: AnnotatedString,
    links: Map<String, String>,
): AnnotatedString =
    if (links.isEmpty()) {
        text
    } else {
        buildAnnotatedString {
            append(text)
            links.forEach { (part, url) ->
                findPartRanges(
                    text.text,
                    listOf(part),
                ).forEach { addLink(LinkAnnotation.Url(url), it.first, it.last + 1) }
            }
        }
    }

private val EMPHASIS_STYLE = SpanStyle(fontWeight = FontWeight.Bold, color = RusuhColors.BrandInk)

private fun styledParts(
    text: AnnotatedString,
    parts: List<String>,
    style: SpanStyle,
): AnnotatedString =
    buildAnnotatedString {
        append(text)
        findPartRanges(text.text, parts).forEach { addStyle(style, it.first, it.last + 1) }
    }

/** Red "!" disc plus message; a polite live region so TalkBack reads the error when it appears. */
@Composable
internal fun ErrorLine(
    text: String,
    modifier: Modifier = Modifier,
    announceText: String = text,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .padding(top = RusuhSpacing.xxs)
                    .size(RusuhSpacing.errorIcon)
                    .background(RusuhColors.BrandDanger, CircleShape)
                    .clearAndSetSemantics {},
        ) {
            Text(
                // The exclamation mark is a glyph, not copy; the disc is hidden from TalkBack.
                text = "!",
                style = RusuhBrandTextStyles.eyebrow.body(),
                color = RusuhColors.BrandPaper,
            )
        }
        Text(
            text = text,
            style = RusuhBrandTextStyles.label.body(),
            color = RusuhColors.BrandDanger,
            modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = announceText },
        )
    }
}

/** Full-width main button with room for its hard shadow (padding on the end and bottom). */
@Composable
internal fun SignInPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    BrutalButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(end = RusuhSpacing.shadowMd, bottom = RusuhSpacing.shadowMd),
    )
}

/** Underlined text action with a 44dp-high touch target. */
@Composable
internal fun LinkText(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = RusuhColors.BrandInk,
    style: TextStyle = RusuhBrandTextStyles.subtitle,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .heightIn(min = RusuhSpacing.actionMinHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = RusuhSpacing.md),
    ) {
        Text(
            text = text,
            style = style.body(),
            color = color,
            textDecoration = TextDecoration.Underline,
        )
    }
}

/** Checkbox row (ink square, magenta when checked); the whole row is the 44dp+ toggle target. */
@Composable
internal fun ConsentRow(
    checked: Boolean,
    text: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = RusuhSpacing.actionMinHeight)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(RusuhSpacing.checkboxSize)
                    .brutalBox(fill = if (checked) RusuhColors.BrandMagenta else RusuhColors.BrandCream),
        ) {
            if (checked) {
                Canvas(Modifier.size(RusuhSpacing.checkboxSize)) {
                    val stroke = RusuhSpacing.borderHeavy.toPx()
                    val color = RusuhColors.BrandPaper
                    val a = Offset(size.width * CHECK_START_X, size.height * CHECK_START_Y)
                    val b = Offset(size.width * CHECK_MID_X, size.height * CHECK_MID_Y)
                    val c = Offset(size.width * CHECK_END_X, size.height * CHECK_END_Y)
                    drawLine(color, a, b, stroke, StrokeCap.Square)
                    drawLine(color, b, c, stroke, StrokeCap.Square)
                }
            }
        }
        Text(
            text = text,
            style = RusuhBrandTextStyles.bodyMedium.body(),
            color = RusuhColors.BrandInk,
            modifier = Modifier.weight(1f),
        )
    }
}

private const val CHECK_START_X = 0.24f
private const val CHECK_START_Y = 0.52f
private const val CHECK_MID_X = 0.44f
private const val CHECK_MID_Y = 0.72f
private const val CHECK_END_X = 0.78f
private const val CHECK_END_Y = 0.30f
