@file:Suppress("LongParameterList")

package com.rusuh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.display
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.otp_input_label
import rusuh_app.shared.generated.resources.otp_input_progress

/**
 * Sign-in code input (Figma "Code"): [length] ink-outlined digit boxes with a hard shadow.
 *
 * Stateless: the caller owns [value] and receives every edit, already sanitised to digits
 * and capped at [length], through [onValueChange]. The caret is pinned to the end on every
 * change, so a tap can never edit mid-string, backspace always removes the last digit and
 * input beyond [length] is ignored. [onComplete] fires on the edit that yields [length]
 * digits, once per completed value (again only after the value changed away and back).
 * Paste works through the invisible text field laid over the boxes (long-press to paste);
 * non-digits are dropped. [separatorAfter] draws a decorative hyphen after that many boxes
 * (Figma "482-091"); it is not part of the value. [strikethrough] draws each digit struck
 * through and without the hard shadow (Figma "Code expired": the old code in disabled boxes).
 * The keyboard is numeric. [autoFocus] requests focus and shows the keyboard when the input
 * enters the composition.
 *
 * Accessibility: the boxes are hidden from the tree and the whole control is one text field
 * announcing "Verification code, N of 6 digits entered" (plus [errorMessage] in the error state).
 *
 * TODO(AUTH-00 #77): one-time-code autofill (Android SMS/email suggestions, iOS
 *  `.oneTimeCode`): Compose Multiplatform 1.7 has no common autofill-hint API, so it needs
 *  platform text-field hooks (expect/actual) once the real flow exists.
 */
@Composable
fun OtpCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = OTP_CODE_LENGTH,
    state: OtpCodeState = OtpCodeState.Default,
    errorMessage: String? = null,
    autoFocus: Boolean = false,
    separatorAfter: Int? = null,
    strikethrough: Boolean = false,
    onComplete: (String) -> Unit = {},
) {
    val digits = sanitizeOtpInput(value, length)
    val label = stringResource(Res.string.otp_input_label)
    val progress = stringResource(Res.string.otp_input_progress, digits.length, length)
    var focused by remember { mutableStateOf(false) }
    val isError = state == OtpCodeState.Error
    val enabled = state != OtpCodeState.Disabled
    val tracker = remember(length) { OtpCompletionTracker(length) }
    tracker.onCurrentValue(digits)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus, enabled) {
        if (autoFocus && enabled) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    // No selection handle or highlight over the invisible text.
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(Color.Transparent, Color.Transparent),
    ) {
        BasicTextField(
            // Selection is always the end of the text, whatever the platform reports.
            value = TextFieldValue(digits, TextRange(digits.length)),
            onValueChange = { raw ->
                val next = sanitizeOtpInput(raw.text, length)
                if (next != digits) {
                    onValueChange(next)
                    if (tracker.shouldComplete(next)) onComplete(next)
                }
            },
            enabled = enabled,
            singleLine = true,
            // Text and caret are invisible: the boxes below render the digits.
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier =
                modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics {
                        contentDescription = label
                        stateDescription = progress
                        if (isError && errorMessage != null) error(errorMessage)
                    }
                    .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
                        // Room for the hard shadow of the last column and row.
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(end = RusuhSpacing.shadowSm, bottom = RusuhSpacing.shadowSm)
                                .clearAndSetSemantics {},
                    ) {
                        repeat(length) { index ->
                            OtpDigitBox(
                                digit = digits.getOrNull(index),
                                active = focused && index == digits.length.coerceAtMost(length - 1),
                                state = state,
                                strikethrough = strikethrough,
                                modifier = Modifier.weight(1f),
                            )
                            if (separatorAfter != null && index == separatorAfter - 1 && index < length - 1) {
                                OtpSeparator()
                            }
                        }
                    }
                    Box(Modifier.matchParentSize()) { inner() }
                }
            },
        )
    }
}

/** Hyphen between the two groups of boxes; centred on the boxes, not on the shadow strip. */
@Composable
private fun OtpSeparator() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(RusuhSpacing.otpBoxHeight),
    ) {
        // The hyphen is drawn, not typed copy: a short ink bar.
        Box(
            Modifier
                .width(RusuhSpacing.otpSeparatorWidth)
                .height(RusuhSpacing.borderHeavy)
                .background(RusuhColors.BrandInk),
        )
    }
}

@Composable
private fun OtpDigitBox(
    digit: Char?,
    active: Boolean,
    state: OtpCodeState,
    modifier: Modifier = Modifier,
    strikethrough: Boolean = false,
) {
    val isError = state == OtpCodeState.Error
    val borderColor =
        when {
            isError -> RusuhColors.BrandDanger
            active -> RusuhColors.BrandMagenta
            else -> RusuhColors.BrandInk
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .height(RusuhSpacing.otpBoxHeight)
                .alpha(if (state == OtpCodeState.Disabled) RusuhSpacing.DISABLED_ALPHA else 1f)
                .brutalBox(
                    fill = if (isError) RusuhColors.BrandDangerSurface else RusuhColors.BrandCream,
                    borderColor = borderColor,
                    shadow = if (digit != null && !strikethrough) borderColor else null,
                    shadowOffset = RusuhSpacing.shadowSm,
                ),
    ) {
        if (digit != null) {
            Text(
                text = digit.toString(),
                style = RusuhBrandTextStyles.stat.display(),
                color = if (isError) RusuhColors.BrandDanger else RusuhColors.BrandInk,
                textDecoration = if (strikethrough) TextDecoration.LineThrough else null,
                textAlign = TextAlign.Center,
            )
        }
    }
}
