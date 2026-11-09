@file:Suppress("MatchingDeclarationName", "LongParameterList")

package com.rusuh.app.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import com.rusuh.app.ui.theme.display

/**
 * Flat "ink-bordered" box used across the Figma-aligned screens: solid [fill], a square
 * [borderWidth] border and an optional hard (blur-free) offset [shadow] drawn behind it.
 */
fun Modifier.brutalBox(
    fill: Color,
    borderColor: Color = RusuhColors.BrandInk,
    borderWidth: Dp = RusuhSpacing.borderMedium,
    shadow: Color? = null,
    shadowOffset: Dp = RusuhSpacing.shadowMd,
): Modifier =
    this
        .drawBehind {
            if (shadow != null) {
                val offset = shadowOffset.toPx()
                drawRect(color = shadow, topLeft = Offset(offset, offset), size = size)
            }
        }
        .background(fill)
        .border(width = borderWidth, color = borderColor)

/**
 * Section header: numbered ink square + display title, a supporting note and a heavy ink rule.
 */
@Composable
fun StepHeader(
    number: Int,
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(RusuhSpacing.sectionNumber).background(RusuhColors.BrandInk),
            ) {
                Text(
                    text = number.toString(),
                    style = RusuhBrandTextStyles.number.display(),
                    color = RusuhColors.BrandPaper,
                )
            }
            Text(
                text = title,
                style = RusuhBrandTextStyles.sectionTitle.display(),
                color = RusuhColors.BrandInk,
            )
        }
        if (note != null) {
            Spacer(Modifier.height(RusuhSpacing.md))
            Text(
                text = note,
                style = RusuhBrandTextStyles.note.body(),
                color = RusuhColors.BrandMuted,
            )
        }
        Spacer(Modifier.height(RusuhSpacing.lg))
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderHeavy).background(RusuhColors.BrandInk))
    }
}

/**
 * Order countdown chip for the top bar (e.g. "4:12"). Switches to the magenta warning
 * treatment when [warn] is true (last minute / expired). Display only: the caller owns the clock.
 */
@Composable
fun CountdownChip(
    text: String,
    description: String,
    modifier: Modifier = Modifier,
    warn: Boolean = false,
) {
    val accent = if (warn) RusuhColors.BrandMagenta else RusuhColors.BrandInk
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .brutalBox(
                    fill = RusuhColors.BrandCream,
                    shadow = accent,
                    shadowOffset = RusuhSpacing.shadowSm,
                )
                .padding(horizontal = RusuhSpacing.lg, vertical = RusuhSpacing.md)
                .semantics { contentDescription = description },
    ) {
        Text(
            text = text,
            style = RusuhBrandTextStyles.counter.display(),
            color = if (warn) RusuhColors.BrandMagentaDeep else RusuhColors.BrandInk,
        )
    }
}

/**
 * Ink-bordered single-line text input (Figma "Input"). Stateless: value changes go through
 * [onValueChange]. [isError] switches to the danger border/surface. [onFocusLost] lets the
 * caller defer validation messages until the user leaves the field.
 */
@Composable
fun BrutalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onFocusLost: () -> Unit = {},
) {
    val borderColor = if (isError) RusuhColors.BrandDanger else RusuhColors.BrandInk
    val fill = if (isError) RusuhColors.BrandDangerSurface else RusuhColors.BrandCream
    val textStyle = RusuhBrandTextStyles.input.body().copy(color = RusuhColors.BrandInk)
    var hadFocus by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(RusuhColors.BrandInk),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { focus ->
                    if (hadFocus && !focus.isFocused) onFocusLost()
                    hadFocus = focus.isFocused
                },
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = RusuhSpacing.inputHeight)
                        .brutalBox(fill = fill, borderColor = borderColor)
                        .padding(horizontal = RusuhSpacing.xl),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = RusuhColors.BrandSubtle,
                    )
                }
                inner()
            }
        },
    )
}

/** Small uppercase "stamp" chip (ink fill, paper text), e.g. "Waktu habis". */
@Composable
fun StampChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = RusuhBrandTextStyles.eyebrow.body(),
        color = RusuhColors.BrandPaper,
        textAlign = TextAlign.Center,
        modifier =
            modifier
                .background(RusuhColors.BrandInk)
                .padding(horizontal = RusuhSpacing.lg, vertical = RusuhSpacing.md),
    )
}

/**
 * Primary CTA: ink fill, paper label and a magenta hard shadow (Figma "Lanjut bayar →").
 * Disabled renders at reduced alpha and ignores taps.
 */
@Composable
fun BrutalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .heightIn(min = RusuhSpacing.ctaHeight)
                .drawBehind {
                    val offset = RusuhSpacing.shadowMd.toPx()
                    drawRect(
                        color = RusuhColors.BrandMagenta.copy(alpha = if (enabled) 1f else RusuhSpacing.DISABLED_ALPHA),
                        topLeft = Offset(offset, offset),
                        size = size,
                    )
                }
                .background(RusuhColors.BrandInk.copy(alpha = if (enabled) 1f else RusuhSpacing.DISABLED_ALPHA))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = RusuhSpacing.xl),
    ) {
        Text(
            // → is the right arrow glyph, not user-visible copy.
            text = "$text →",
            style = RusuhBrandTextStyles.cta.display(),
            color = RusuhColors.BrandPaper,
            textAlign = TextAlign.Center,
        )
    }
}
