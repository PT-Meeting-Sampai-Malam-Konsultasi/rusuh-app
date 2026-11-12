@file:Suppress("LongParameterList")

package com.rusuh.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.rusuh.app.ui.components.BrutalTextField
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import com.rusuh.app.ui.theme.display
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.detail_name_field_description
import rusuh_app.shared.generated.resources.detail_name_hint
import rusuh_app.shared.generated.resources.detail_name_label
import rusuh_app.shared.generated.resources.detail_name_placeholder
import rusuh_app.shared.generated.resources.detail_nik_duplicate
import rusuh_app.shared.generated.resources.detail_nik_example
import rusuh_app.shared.generated.resources.detail_nik_field_description
import rusuh_app.shared.generated.resources.detail_nik_invalid
import rusuh_app.shared.generated.resources.detail_nik_label
import rusuh_app.shared.generated.resources.detail_nik_placeholder
import rusuh_app.shared.generated.resources.detail_ticket_label

/**
 * One attendee block ("Tiket 1 · Festival B"): name as on the KTP and NIK.
 *
 * @param duplicateOf 1-based earlier ticket that already uses this NIK, or null.
 */
@Composable
internal fun AttendeeBlock(
    ticketNumber: Int,
    categoryName: String,
    attendee: AttendeeForm,
    duplicateOf: Int?,
    onNameChange: (String) -> Unit,
    onNikChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLast: Boolean = true,
) {
    var nikTouched by remember { mutableStateOf(false) }
    var nikFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val formatError = nikTouched && attendee.nik.isNotEmpty() && !isNikValid(attendee.nik)
    val nikError =
        when {
            duplicateOf != null -> stringResource(Res.string.detail_nik_duplicate, duplicateOf)
            formatError -> stringResource(Res.string.detail_nik_invalid, NIK_LENGTH)
            else -> null
        }
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = RusuhSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
    ) {
        Text(
            text = stringResource(Res.string.detail_ticket_label, ticketNumber, categoryName),
            style = RusuhBrandTextStyles.itemTitle.display(),
            color = RusuhColors.BrandInk,
        )

        FieldLabel(stringResource(Res.string.detail_name_label))
        BrutalTextField(
            value = attendee.name,
            onValueChange = onNameChange,
            placeholder = stringResource(Res.string.detail_name_placeholder),
            label = stringResource(Res.string.detail_name_field_description, ticketNumber),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
        )
        FieldNote(stringResource(Res.string.detail_name_hint))

        FieldLabel(stringResource(Res.string.detail_nik_label))
        BrutalTextField(
            value = attendee.nik,
            onValueChange = onNikChange,
            placeholder = stringResource(Res.string.detail_nik_placeholder),
            label = stringResource(Res.string.detail_nik_field_description, ticketNumber),
            isError = nikError != null,
            errorMessage = nikError,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
                ),
            keyboardActions =
                KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                    onDone = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    },
                ),
            // NIK is PII: masked to the last 4 digits whenever the field is not being edited.
            visualTransformation = NikVisualTransformation(masked = !nikFocused),
            onFocusChange = { nikFocused = it },
            onFocusLost = { nikTouched = true },
        )
        if (nikError != null) FieldError(nikError) else FieldNote(stringResource(Res.string.detail_nik_example))
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderThin).background(RusuhColors.BrandHairline))
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text = text, style = RusuhBrandTextStyles.label.body(), color = RusuhColors.BrandInk)
}

@Composable
internal fun FieldNote(text: String) {
    Text(text = text, style = RusuhBrandTextStyles.note.body(), color = RusuhColors.BrandMuted)
}

/** Danger message with the red "!" badge (Figma error row). */
@Composable
private fun FieldError(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(RusuhSpacing.xl)
                    .background(RusuhColors.BrandDanger, CircleShape)
                    .clearAndSetSemantics { },
        ) {
            // "!" is a symbol glyph, not user-visible copy.
            Text(text = "!", style = RusuhBrandTextStyles.eyebrow.body(), color = Color.White)
        }
        Text(
            text = text,
            style = RusuhBrandTextStyles.note.body().copy(fontWeight = FontWeight.SemiBold),
            color = RusuhColors.BrandDanger,
        )
    }
}

/**
 * Displays NIK digits grouped by four; when [masked] all but the last four digits are bullets
 * ("•••• •••• •••• 1234"). Stored value stays digits-only. Both modes have the same length, so
 * one offset mapping serves both.
 */
internal class NikVisualTransformation(private val masked: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = nikOriginalToTransformed(offset)

                override fun transformedToOriginal(offset: Int): Int = nikTransformedToOriginal(offset, text.length)
            }
        return TransformedText(AnnotatedString(nikDisplay(text.text, masked)), mapping)
    }
}
