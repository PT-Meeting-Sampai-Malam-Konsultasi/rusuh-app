package com.rusuh.app.feature.consent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.rusuh.app.ui.components.brutalBox
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import com.rusuh.app.ui.theme.display
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.consent_accept
import rusuh_app.shared.generated.resources.consent_body
import rusuh_app.shared.generated.resources.consent_collected_item_1
import rusuh_app.shared.generated.resources.consent_collected_item_2
import rusuh_app.shared.generated.resources.consent_collected_item_3
import rusuh_app.shared.generated.resources.consent_collected_title
import rusuh_app.shared.generated.resources.consent_decline
import rusuh_app.shared.generated.resources.consent_not_collected_item_1
import rusuh_app.shared.generated.resources.consent_not_collected_item_2
import rusuh_app.shared.generated.resources.consent_not_collected_title
import rusuh_app.shared.generated.resources.consent_title
import rusuh_app.shared.generated.resources.home_logo_accent
import rusuh_app.shared.generated.resources.home_logo_wordmark

@Composable
fun ConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(RusuhColors.BrandPaper),
    ) {
        ConsentTopBar()
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RusuhSpacing.xl, vertical = RusuhSpacing.xl)
                    .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(Res.string.consent_title),
                style = RusuhBrandTextStyles.sheetHeadline.display(),
                color = RusuhColors.BrandInk,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(RusuhSpacing.lg))
            Text(
                text = stringResource(Res.string.consent_body),
                style = RusuhBrandTextStyles.bodyLarge.body(),
                color = RusuhColors.BrandMuted,
            )
            Spacer(Modifier.height(RusuhSpacing.xxl))
            CollectedSection()
            Spacer(Modifier.height(RusuhSpacing.xxl))
            NotCollectedSection()
            Spacer(Modifier.height(RusuhSpacing.xxl))
            ConsentButtons(onAccept = onAccept, onDecline = onDecline)
            Spacer(Modifier.height(RusuhSpacing.xl))
        }
    }
}

@Composable
private fun ConsentTopBar() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(RusuhColors.BrandPaper)
                .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(RusuhSpacing.appBarHeight)
                    .semantics(mergeDescendants = true) {},
        ) {
            val wordmark = stringResource(Res.string.home_logo_wordmark)
            val accent = stringResource(Res.string.home_logo_accent)
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) { this.heading() },
            ) {
                Text(
                    text = wordmark,
                    style = RusuhBrandTextStyles.hero.display(),
                    color = RusuhColors.BrandInk,
                )
                Text(
                    text = accent,
                    style = RusuhBrandTextStyles.hero.display(),
                    color = RusuhColors.Pink,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderHeavy).background(RusuhColors.BrandInk))
    }
}

@Composable
private fun CollectedSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            number = 1,
            title = stringResource(Res.string.consent_collected_title),
        )
        Spacer(Modifier.height(RusuhSpacing.md))
        BulletItem(stringResource(Res.string.consent_collected_item_1))
        BulletItem(stringResource(Res.string.consent_collected_item_2))
        BulletItem(stringResource(Res.string.consent_collected_item_3))
    }
}

@Composable
private fun NotCollectedSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            number = 2,
            title = stringResource(Res.string.consent_not_collected_title),
        )
        Spacer(Modifier.height(RusuhSpacing.md))
        BulletItem(stringResource(Res.string.consent_not_collected_item_1))
        BulletItem(stringResource(Res.string.consent_not_collected_item_2))
    }
}

@Composable
private fun SectionHeader(
    number: Int,
    title: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
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
        Spacer(Modifier.height(RusuhSpacing.sm))
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderHeavy).background(RusuhColors.BrandInk))
    }
}

@Composable
private fun BulletItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = RusuhSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
    ) {
        Text(
            text = "•",
            style = RusuhBrandTextStyles.bodyLarge.body(),
            color = RusuhColors.BrandInk,
        )
        Text(
            text = text,
            style = RusuhBrandTextStyles.bodyLarge.body(),
            color = RusuhColors.BrandInk,
        )
    }
}

@Composable
private fun ConsentButtons(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(end = RusuhSpacing.shadowMd, bottom = RusuhSpacing.shadowMd),
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = RusuhSpacing.ctaHeight)
                    .brutalBox(fill = RusuhColors.Green, shadow = RusuhColors.BrandInk)
                    .clickable(role = Role.Button, onClick = onAccept)
                    .padding(horizontal = RusuhSpacing.xxl, vertical = RusuhSpacing.lg),
        ) {
            Text(
                text = stringResource(Res.string.consent_accept),
                style = RusuhBrandTextStyles.cta.display(),
                color = RusuhColors.BrandPaper,
                textAlign = TextAlign.Center,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = RusuhSpacing.ctaHeight)
                    .brutalBox(fill = RusuhColors.BrandCream, shadow = RusuhColors.BrandInk)
                    .clickable(role = Role.Button, onClick = onDecline)
                    .padding(horizontal = RusuhSpacing.xxl, vertical = RusuhSpacing.lg),
        ) {
            Text(
                text = stringResource(Res.string.consent_decline),
                style = RusuhBrandTextStyles.cta.display(),
                color = RusuhColors.BrandInk,
                textAlign = TextAlign.Center,
            )
        }
    }
}
