package com.rusuh.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.home_language_toggle_description
import rusuh_app.shared.generated.resources.home_logo_accent
import rusuh_app.shared.generated.resources.home_logo_description
import rusuh_app.shared.generated.resources.home_logo_wordmark

/** Paper app bar: RUSUH! wordmark on the left, ID | EN language toggle on the right, heavy ink rule below. */
@Composable
internal fun HomeAppBar(
    selectedLanguage: HomeLanguage,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(RusuhColors.Paper)
                .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(RusuhSpacing.appBarHeight)
                    .padding(horizontal = RusuhSpacing.xl),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeLogo()
            LanguageToggle(selected = selectedLanguage)
        }
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.ruleThick).background(RusuhColors.Ink))
    }
}

@Composable
private fun HomeLogo(modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.home_logo_description)
    val wordmark = stringResource(Res.string.home_logo_wordmark)
    val accent = stringResource(Res.string.home_logo_accent)
    Text(
        text =
            buildAnnotatedString {
                append(wordmark)
                withStyle(SpanStyle(color = RusuhColors.Pink)) { append(accent) }
            },
        style = MaterialTheme.typography.displaySmall,
        color = RusuhColors.Ink,
        modifier = modifier.semantics { contentDescription = description },
    )
}

/**
 * Pill-shaped ID | EN toggle (look only for now: language switching is wired with
 * the i18n runtime work, so [onSelect] defaults to a no-op).
 */
@Composable
private fun LanguageToggle(
    selected: HomeLanguage,
    modifier: Modifier = Modifier,
    onSelect: (HomeLanguage) -> Unit = {},
) {
    val description = stringResource(Res.string.home_language_toggle_description)
    Row(
        modifier =
            modifier
                .border(RusuhSpacing.stampBorder, RusuhColors.Ink, CircleShape)
                .padding(RusuhSpacing.xs)
                .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.xxs),
    ) {
        HomeLanguage.entries.forEach { language ->
            val isSelected = language == selected
            Text(
                text = language.code,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) RusuhColors.Paper else RusuhColors.Ink,
                modifier =
                    Modifier
                        .background(if (isSelected) RusuhColors.Ink else Color.Transparent, CircleShape)
                        .clickable(role = Role.RadioButton) { onSelect(language) }
                        .semantics { this.selected = isSelected }
                        .padding(horizontal = RusuhSpacing.lg, vertical = RusuhSpacing.xs),
            )
        }
    }
}
