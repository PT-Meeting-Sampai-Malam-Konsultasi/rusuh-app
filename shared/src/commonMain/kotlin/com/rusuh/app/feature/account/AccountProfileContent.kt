@file:Suppress("LongParameterList", "TooManyFunctions")

package com.rusuh.app.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.rusuh.app.ui.components.brutalBox
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import com.rusuh.app.ui.theme.display
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.account_analytics_disabled
import rusuh_app.shared.generated.resources.account_analytics_enabled
import rusuh_app.shared.generated.resources.account_analytics_label
import rusuh_app.shared.generated.resources.account_change_email
import rusuh_app.shared.generated.resources.account_delete
import rusuh_app.shared.generated.resources.account_email_label
import rusuh_app.shared.generated.resources.account_email_row_description
import rusuh_app.shared.generated.resources.account_login_method_label
import rusuh_app.shared.generated.resources.account_login_method_value
import rusuh_app.shared.generated.resources.account_sign_in_section
import rusuh_app.shared.generated.resources.account_sign_out
import rusuh_app.shared.generated.resources.account_verified
import rusuh_app.shared.generated.resources.debug_send_test_crash
import rusuh_app.shared.generated.resources.debug_send_test_nonfatal

/**
 * Signed-in Account profile body (design "Account · Profile", without NIK and without the
 * email-preferences section). Stateless; it renders only the content between the tab top bar and
 * the bottom bar, so the Account route can drop it into its signed-in branch (`AccountRoute` feeds it from
 * `GET /me`; "Keluar" signs out).
 */
@Composable
fun AccountProfileContent(
    profile: AccountProfileUi,
    actions: AccountProfileActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(RusuhColors.BrandPaper)
                .verticalScroll(rememberScrollState())
                .padding(RusuhSpacing.xl),
    ) {
        ProfileHeader(profile)
        Spacer(Modifier.height(RusuhSpacing.xxl))
        SignInSection(profile, actions.onChangeEmail)
        Spacer(Modifier.height(RusuhSpacing.xxl))
        AnalyticsRow(profile.analyticsEnabled, actions.onToggleAnalytics)
        if (actions.onTestCrash != null || actions.onTestNonFatal != null) {
            Spacer(Modifier.height(RusuhSpacing.md))
            DebugRows(actions.onTestCrash, actions.onTestNonFatal)
        }
        Spacer(Modifier.height(RusuhSpacing.xxl))
        AccountButtons(actions)
    }
}

@Composable
private fun ProfileHeader(profile: AccountProfileUi) {
    Column(verticalArrangement = Arrangement.spacedBy(RusuhSpacing.sm)) {
        Text(
            text = profile.name,
            style = RusuhBrandTextStyles.sheetHeadline.display(),
            color = RusuhColors.BrandInk,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = profile.email,
            style = RusuhBrandTextStyles.bodyLarge.body(),
            color = RusuhColors.BrandMuted,
        )
    }
}

@Composable
private fun SignInSection(
    profile: AccountProfileUi,
    onChangeEmail: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            number = 1,
            title = stringResource(Res.string.account_sign_in_section),
            actionLabel = onChangeEmail?.let { stringResource(Res.string.account_change_email) },
            onAction = onChangeEmail,
        )
        EmailRow(profile)
        Hairline()
        LabeledRow(
            label = stringResource(Res.string.account_login_method_label),
            value = stringResource(Res.string.account_login_method_value),
        )
        Hairline()
    }
}

@Composable
private fun SectionHeader(
    number: Int,
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
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
            if (actionLabel != null && onAction != null) {
                UnderlinedLink(text = actionLabel, onClick = onAction)
            }
        }
        Spacer(Modifier.height(RusuhSpacing.md))
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderHeavy).background(RusuhColors.BrandInk))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmailRow(profile: AccountProfileUi) {
    val verified = stringResource(Res.string.account_verified)
    val description = stringResource(Res.string.account_email_row_description, profile.email, verified)
    Column(
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.xs),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = RusuhSpacing.lg)
                .semantics(mergeDescendants = true) {
                    if (profile.emailVerified) contentDescription = description
                },
    ) {
        Text(
            text = stringResource(Res.string.account_email_label),
            style = RusuhBrandTextStyles.eyebrow.body(),
            color = RusuhColors.BrandSubtle,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RusuhSpacing.xs),
        ) {
            Text(
                text = profile.email,
                style = RusuhBrandTextStyles.subtitle.display(),
                color = RusuhColors.BrandInk,
            )
            if (profile.emailVerified) {
                // The check mark is a glyph, not user-visible copy.
                Text(
                    text = "✓ ${verified.uppercase()}",
                    style = RusuhBrandTextStyles.eyebrow.body(),
                    color = RusuhColors.Green,
                )
            }
        }
    }
}

@Composable
private fun LabeledRow(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.xs),
        modifier = Modifier.fillMaxWidth().padding(vertical = RusuhSpacing.lg).semantics(mergeDescendants = true) {},
    ) {
        Text(text = label, style = RusuhBrandTextStyles.eyebrow.body(), color = RusuhColors.BrandSubtle)
        Text(text = value, style = RusuhBrandTextStyles.bodyLarge.body(), color = RusuhColors.BrandInk)
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderThin).background(RusuhColors.BrandHairline))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountButtons(actions: AccountProfileActions) {
    // FlowRow: at large font or narrow width the link drops to its own line instead of squeezing.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.xl),
        modifier = Modifier.padding(end = RusuhSpacing.shadowMd, bottom = RusuhSpacing.shadowMd),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .heightIn(min = RusuhSpacing.ctaHeight)
                    .brutalBox(fill = RusuhColors.BrandCream, shadow = RusuhColors.BrandInk)
                    .clickable(role = Role.Button, onClick = actions.onSignOut)
                    .padding(horizontal = RusuhSpacing.xxl, vertical = RusuhSpacing.lg),
        ) {
            Text(
                text = stringResource(Res.string.account_sign_out),
                style = RusuhBrandTextStyles.cta.display(),
                color = RusuhColors.BrandInk,
                textAlign = TextAlign.Center,
            )
        }
        // TODO(ACCOUNT-00 #104): no delete-account endpoint in the contract yet: null callback = plain text.
        UnderlinedLink(text = stringResource(Res.string.account_delete), onClick = actions.onDeleteAccount)
    }
}

@Composable
internal fun AnalyticsRow(
    enabled: Boolean,
    onToggle: ((Boolean) -> Unit)?,
) {
    val valueText =
        stringResource(
            if (enabled) Res.string.account_analytics_enabled else Res.string.account_analytics_disabled,
        )
    Column(
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.xs),
        modifier =
            Modifier
                .fillMaxWidth()
                .let { base ->
                    if (onToggle != null) {
                        base.toggleable(value = enabled, role = Role.Switch, onValueChange = onToggle)
                    } else {
                        base
                    }
                }
                .padding(vertical = RusuhSpacing.lg)
                .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = stringResource(Res.string.account_analytics_label),
            style = RusuhBrandTextStyles.eyebrow.body(),
            color = RusuhColors.BrandSubtle,
        )
        Text(
            text = valueText,
            style = RusuhBrandTextStyles.subtitle.display(),
            color = RusuhColors.BrandInk,
        )
    }
}

@Composable
internal fun DebugRows(
    onTestCrash: (() -> Unit)?,
    onTestNonFatal: (() -> Unit)?,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.sm),
        modifier = Modifier.fillMaxWidth().padding(vertical = RusuhSpacing.sm),
    ) {
        if (onTestCrash != null) {
            DebugActionRow(stringResource(Res.string.debug_send_test_crash), onTestCrash)
        }
        if (onTestNonFatal != null) {
            DebugActionRow(stringResource(Res.string.debug_send_test_nonfatal), onTestNonFatal)
        }
    }
}

@Composable
private fun DebugActionRow(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = RusuhBrandTextStyles.label.body(),
        color = RusuhColors.BrandSubtle,
        textDecoration = TextDecoration.Underline,
        modifier =
            Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = RusuhSpacing.sm),
    )
}

// TODO(ACCOUNT-00 #104): SectionHeader/UnderlinedLink duplicate StepHeader/LinkText; share after the login PR.
@Composable
private fun UnderlinedLink(
    text: String,
    onClick: (() -> Unit)?,
) {
    val base = Modifier.heightIn(min = RusuhSpacing.actionMinHeight)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            if (onClick != null) {
                base.clickable(role = Role.Button, onClick = onClick).padding(horizontal = RusuhSpacing.xs)
            } else {
                // No action yet: same look, but TalkBack announces it as disabled, no ripple or button role.
                base.padding(horizontal = RusuhSpacing.xs).semantics { disabled() }
            },
    ) {
        Text(
            text = text,
            style = RusuhBrandTextStyles.label.body(),
            color = RusuhColors.BrandInk,
            textDecoration = TextDecoration.Underline,
        )
    }
}
