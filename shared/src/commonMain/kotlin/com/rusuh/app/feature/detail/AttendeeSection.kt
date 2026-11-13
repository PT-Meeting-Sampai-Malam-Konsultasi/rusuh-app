@file:Suppress("LongParameterList")

package com.rusuh.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
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
import rusuh_app.shared.generated.resources.detail_validation_checking
import rusuh_app.shared.generated.resources.detail_validation_duplicate
import rusuh_app.shared.generated.resources.detail_validation_nik_invalid
import rusuh_app.shared.generated.resources.detail_validation_retry
import rusuh_app.shared.generated.resources.detail_validation_seat_taken
import rusuh_app.shared.generated.resources.detail_validation_unavailable
import rusuh_app.shared.generated.resources.detail_validation_unavailable_network
import rusuh_app.shared.generated.resources.detail_validation_unavailable_rate
import rusuh_app.shared.generated.resources.detail_validation_unavailable_service
import rusuh_app.shared.generated.resources.detail_validation_valid

/**
 * One attendee block ("Tiket 1 · Festival B"): name as on the KTP and NIK.
 *
 * @param duplicateOf 1-based earlier ticket that already uses this NIK, or null.
 * @param validationStatus inline server validation status for this attendee.
 * @param onRetryValidation callback when user taps retry on an unavailable validation.
 */
@Composable
internal fun AttendeeBlock(
    ticketNumber: Int,
    categoryName: String,
    attendee: AttendeeForm,
    duplicateOf: Int?,
    validationStatus: AttendeeValidationStatus,
    onNameChange: (String) -> Unit,
    onNikChange: (String) -> Unit,
    onRetryValidation: () -> Unit,
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
            visualTransformation = NikVisualTransformation(masked = !nikFocused),
            onFocusChange = { nikFocused = it },
            onFocusLost = { nikTouched = true },
        )

        when {
            nikError != null -> FieldError(nikError)
            else -> ValidationStatusRow(validationStatus, onRetryValidation)
        }

        Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderThin).background(RusuhColors.BrandHairline))
    }
}

@Composable
private fun ValidationStatusRow(
    status: AttendeeValidationStatus,
    onRetry: () -> Unit,
) {
    when (status) {
        AttendeeValidationStatus.None -> {
            FieldNote(stringResource(Res.string.detail_nik_example))
        }
        AttendeeValidationStatus.Checking -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = RusuhColors.BrandMuted,
                )
                Text(
                    text = stringResource(Res.string.detail_validation_checking),
                    style = RusuhBrandTextStyles.note.body(),
                    color = RusuhColors.BrandMuted,
                )
            }
        }
        AttendeeValidationStatus.Valid -> {
            ValidationSuccess(stringResource(Res.string.detail_validation_valid))
        }
        is AttendeeValidationStatus.Rejected -> {
            val message =
                when (status.reason) {
                    RejectedReason.NIK_INVALID -> stringResource(Res.string.detail_validation_nik_invalid)
                    RejectedReason.SEAT_TAKEN -> stringResource(Res.string.detail_validation_seat_taken)
                    RejectedReason.DUPLICATE -> stringResource(Res.string.detail_validation_duplicate)
                }
            FieldError(message)
        }
        is AttendeeValidationStatus.Unavailable -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        stringResource(
                            when (status.reason) {
                                UnavailableReason.NETWORK -> Res.string.detail_validation_unavailable_network
                                UnavailableReason.SERVICE -> Res.string.detail_validation_unavailable_service
                                UnavailableReason.TOO_MANY_ATTEMPTS -> Res.string.detail_validation_unavailable_rate
                                UnavailableReason.SERVER -> Res.string.detail_validation_unavailable
                            },
                        ),
                    style = RusuhBrandTextStyles.note.body(),
                    color = RusuhColors.BrandMuted,
                )
                Text(
                    text = stringResource(Res.string.detail_validation_retry),
                    style = RusuhBrandTextStyles.note.body().copy(fontWeight = FontWeight.SemiBold),
                    color = RusuhColors.BrandPrimary,
                    modifier = Modifier.clickable { onRetry() },
                )
            }
        }
    }
}

@Composable
private fun ValidationSuccess(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(RusuhSpacing.xl)
                    .background(RusuhColors.BrandSuccess, CircleShape)
                    .clearAndSetSemantics { },
        ) {
            Text(text = "✓", style = RusuhBrandTextStyles.eyebrow.body(), color = Color.White)
        }
        Text(
            text = text,
            style = RusuhBrandTextStyles.note.body().copy(fontWeight = FontWeight.SemiBold),
            color = RusuhColors.BrandSuccess,
        )
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
