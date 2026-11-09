@file:Suppress("LongParameterList")

package com.rusuh.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.rusuh.app.ui.components.BrutalTextField
import com.rusuh.app.ui.components.brutalBox
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import com.rusuh.app.ui.theme.display
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.detail_holder_other
import rusuh_app.shared.generated.resources.detail_holder_self
import rusuh_app.shared.generated.resources.detail_holder_toggle_description
import rusuh_app.shared.generated.resources.detail_name_hint
import rusuh_app.shared.generated.resources.detail_name_label
import rusuh_app.shared.generated.resources.detail_name_placeholder
import rusuh_app.shared.generated.resources.detail_nik_duplicate
import rusuh_app.shared.generated.resources.detail_nik_example
import rusuh_app.shared.generated.resources.detail_nik_invalid
import rusuh_app.shared.generated.resources.detail_nik_label
import rusuh_app.shared.generated.resources.detail_nik_placeholder
import rusuh_app.shared.generated.resources.detail_ticket_label

/**
 * One attendee block ("Tiket 1 · Festival B"): self/other toggle, name as on the KTP and NIK.
 * The account has no NIK, so [AttendeeKind.Self] only prefills the name (done by the caller via
 * [withKind]); the NIK is always typed.
 *
 * @param duplicateOf 1-based earlier ticket that already uses this NIK, or null.
 */
@Composable
internal fun AttendeeBlock(
    ticketNumber: Int,
    categoryName: String,
    attendee: AttendeeForm,
    duplicateOf: Int?,
    onKindChange: (AttendeeKind) -> Unit,
    onNameChange: (String) -> Unit,
    onNikChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nikTouched by remember { mutableStateOf(false) }
    val formatError = nikTouched && attendee.nik.isNotEmpty() && !isNikValid(attendee.nik)
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = RusuhSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
    ) {
        Text(
            text = stringResource(Res.string.detail_ticket_label, ticketNumber, categoryName),
            style = RusuhBrandTextStyles.itemTitle.display(),
            color = RusuhColors.BrandInk,
        )
        HolderToggle(selected = attendee.kind, onSelect = onKindChange)

        FieldLabel(stringResource(Res.string.detail_name_label))
        BrutalTextField(
            value = attendee.name,
            onValueChange = onNameChange,
            placeholder = stringResource(Res.string.detail_name_placeholder),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        FieldNote(stringResource(Res.string.detail_name_hint))

        FieldLabel(stringResource(Res.string.detail_nik_label))
        BrutalTextField(
            value = attendee.nik,
            onValueChange = onNikChange,
            placeholder = stringResource(Res.string.detail_nik_placeholder),
            isError = formatError || duplicateOf != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = NikVisualTransformation,
            onFocusLost = { nikTouched = true },
        )
        when {
            duplicateOf != null -> FieldError(stringResource(Res.string.detail_nik_duplicate, duplicateOf))
            formatError -> FieldError(stringResource(Res.string.detail_nik_invalid, NIK_LENGTH))
            else -> FieldNote(stringResource(Res.string.detail_nik_example))
        }
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderThin).background(RusuhColors.BrandHairline))
    }
}

/** Two-cell segmented radio group: "Saya sendiri" | "Orang lain". */
@Composable
private fun HolderToggle(
    selected: AttendeeKind,
    onSelect: (AttendeeKind) -> Unit,
) {
    val groupDescription = stringResource(Res.string.detail_holder_toggle_description)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .brutalBox(fill = RusuhColors.BrandCream)
                .selectableGroup()
                .semantics { contentDescription = groupDescription },
    ) {
        ToggleCell(
            text = stringResource(Res.string.detail_holder_self),
            selected = selected == AttendeeKind.Self,
            onClick = { onSelect(AttendeeKind.Self) },
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(RusuhSpacing.borderHeavy).fillMaxHeight().background(RusuhColors.BrandInk))
        ToggleCell(
            text = stringResource(Res.string.detail_holder_other),
            selected = selected == AttendeeKind.Other,
            onClick = { onSelect(AttendeeKind.Other) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ToggleCell(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .heightIn(min = RusuhSpacing.toggleHeight)
                .background(if (selected) RusuhColors.BrandChip else RusuhColors.BrandCream)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Text(
            text = text,
            style = RusuhBrandTextStyles.label.body(),
            color = if (selected) RusuhColors.BrandInk else RusuhColors.BrandSubtle,
            textAlign = TextAlign.Center,
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

/** Danger message with the red "!" badge (Figma error row). */
@Composable
private fun FieldError(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(RusuhSpacing.xl).background(RusuhColors.BrandDanger, CircleShape),
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

/** Displays NIK digits grouped by four ("3174 0552 0396 0004"); stored value stays digits-only. */
internal object NikVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val grouped = groupNik(text.text)
        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = offset + (offset - 1).coerceAtLeast(0) / 4

                override fun transformedToOriginal(offset: Int): Int = (offset - offset / 5).coerceAtMost(text.length)
            }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}
