package com.rusuh.app.feature.queues

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.rusuh.app.ui.theme.RusuhBrandColors
import com.rusuh.app.ui.theme.RusuhBrandMetrics
import com.rusuh.app.ui.theme.RusuhShapes
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.my_queues_language_en
import rusuh_app.shared.generated.resources.my_queues_language_group
import rusuh_app.shared.generated.resources.my_queues_language_id
import rusuh_app.shared.generated.resources.my_queues_logo_description

/** App bar: RUSUH! logo (pink bang) on the left, ID|EN toggle on the right, ink rule below. */
@Composable
internal fun MyQueuesHeader(
    language: AppLanguageOption,
    onLanguageSelected: (AppLanguageOption) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(RusuhBrandColors.Paper)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(RusuhBrandMetrics.appBarHeight)
                    .padding(horizontal = RusuhSpacing.xl),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    buildAnnotatedString {
                        append("RUSUH")
                        withStyle(SpanStyle(color = RusuhBrandColors.Pink)) { append("!") }
                    },
                style = MaterialTheme.typography.headlineMedium,
                fontSize = LOGO_FONT_SIZE,
                fontWeight = FontWeight.ExtraBold,
                color = RusuhBrandColors.Ink,
                modifier = Modifier.logoDescription(),
            )
            LanguageToggle(selected = language, onSelected = onLanguageSelected)
        }
        Box(Modifier.fillMaxWidth().height(RusuhBrandMetrics.borderWidth).background(RusuhBrandColors.Ink))
    }
}

@Composable
private fun Modifier.logoDescription(): Modifier {
    val description = stringResource(Res.string.my_queues_logo_description)
    return semantics { contentDescription = description }
}

/** Pill toggle: the selected option is a filled ink segment with paper text. */
@Composable
private fun LanguageToggle(
    selected: AppLanguageOption,
    onSelected: (AppLanguageOption) -> Unit,
) {
    val groupDescription = stringResource(Res.string.my_queues_language_group)
    Row(
        modifier =
            Modifier
                .border(RusuhBrandMetrics.borderWidth, RusuhBrandColors.Ink, RusuhShapes.pill)
                .padding(RusuhSpacing.xs)
                .semantics { contentDescription = groupDescription },
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.xxs),
    ) {
        AppLanguageOption.entries.forEach { option ->
            val isSelected = option == selected
            Text(
                text =
                    stringResource(
                        if (option == AppLanguageOption.Indonesian) {
                            Res.string.my_queues_language_id
                        } else {
                            Res.string.my_queues_language_en
                        },
                    ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) RusuhBrandColors.Paper else RusuhBrandColors.Ink,
                modifier =
                    Modifier
                        .background(if (isSelected) RusuhBrandColors.Ink else Color.Transparent, RusuhShapes.pill)
                        .clickable(role = Role.RadioButton) { onSelected(option) }
                        .semantics { this.selected = isSelected }
                        .padding(horizontal = RusuhSpacing.lg, vertical = RusuhSpacing.xs),
            )
        }
    }
}

private val LOGO_FONT_SIZE = 28.sp
