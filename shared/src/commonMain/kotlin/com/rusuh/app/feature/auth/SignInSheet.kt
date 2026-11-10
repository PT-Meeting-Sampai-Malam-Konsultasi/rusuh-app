package com.rusuh.app.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import com.rusuh.app.ui.components.BrutalButton
import com.rusuh.app.ui.components.BrutalTextField
import com.rusuh.app.ui.components.OtpCodeInput
import com.rusuh.app.ui.components.OtpCodeState
import com.rusuh.app.ui.components.StampChip
import com.rusuh.app.ui.components.brutalBox
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import com.rusuh.app.ui.theme.display
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.sign_in_account_notice
import rusuh_app.shared.generated.resources.sign_in_badge
import rusuh_app.shared.generated.resources.sign_in_change_email
import rusuh_app.shared.generated.resources.sign_in_code_body
import rusuh_app.shared.generated.resources.sign_in_code_headline
import rusuh_app.shared.generated.resources.sign_in_consent
import rusuh_app.shared.generated.resources.sign_in_create_account
import rusuh_app.shared.generated.resources.sign_in_dismiss
import rusuh_app.shared.generated.resources.sign_in_email_body
import rusuh_app.shared.generated.resources.sign_in_email_headline
import rusuh_app.shared.generated.resources.sign_in_email_label
import rusuh_app.shared.generated.resources.sign_in_email_notice
import rusuh_app.shared.generated.resources.sign_in_email_placeholder
import rusuh_app.shared.generated.resources.sign_in_expired_body
import rusuh_app.shared.generated.resources.sign_in_expired_headline
import rusuh_app.shared.generated.resources.sign_in_name_label
import rusuh_app.shared.generated.resources.sign_in_name_placeholder
import rusuh_app.shared.generated.resources.sign_in_new_body
import rusuh_app.shared.generated.resources.sign_in_new_headline
import rusuh_app.shared.generated.resources.sign_in_resend
import rusuh_app.shared.generated.resources.sign_in_resend_wait
import rusuh_app.shared.generated.resources.sign_in_send_code
import rusuh_app.shared.generated.resources.sign_in_sheet_description
import rusuh_app.shared.generated.resources.sign_in_wrong_attempts
import rusuh_app.shared.generated.resources.sign_in_wrong_headline

/**
 * Sign-in bottom sheet (AUTH-00-M2, Figma Login section): dim scrim plus a paper sheet
 * pinned to the bottom, same construction as the Detail "Waktu habis" sheet. Renders the
 * five [SignInUiState] cases; stateless, all edits go through [actions]. Place it on top of
 * the host screen (for example as a sibling inside a `Box`).
 */
@Composable
fun SignInSheet(
    state: SignInUiState,
    modifier: Modifier = Modifier,
    actions: SignInActions = SignInActions(),
) {
    val description = stringResource(Res.string.sign_in_sheet_description)
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(RusuhColors.ExpiredScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = actions.onDismiss,
                    ),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .background(RusuhColors.BrandPaper)
                    .semantics { contentDescription = description },
        ) {
            Box(Modifier.fillMaxWidth().height(RusuhSpacing.borderHeavy).background(RusuhColors.BrandInk))
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = RusuhSpacing.xxl, vertical = RusuhSpacing.xxl),
            ) {
                when (state) {
                    is SignInUiState.Email -> EmailStep(state, actions)
                    is SignInUiState.Code ->
                        CodeStep(
                            email = state.email,
                            code = state.code,
                            resendInSeconds = state.resendInSeconds,
                            attemptsLeft = null,
                            actions = actions,
                        )
                    is SignInUiState.WrongCode ->
                        CodeStep(
                            email = state.email,
                            code = state.code,
                            resendInSeconds = state.resendInSeconds,
                            attemptsLeft = state.attemptsLeft,
                            actions = actions,
                        )
                    is SignInUiState.CodeExpired -> ExpiredStep(actions)
                    is SignInUiState.NewAccount -> NewAccountStep(state, actions)
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    headline: String,
    body: String,
) {
    StampChip(text = stringResource(Res.string.sign_in_badge))
    Spacer(Modifier.height(RusuhSpacing.xl))
    Text(
        text = headline,
        style = RusuhBrandTextStyles.sheetHeadline.display(),
        color = RusuhColors.BrandInk,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(RusuhSpacing.lg))
    Text(
        text = body,
        style = RusuhBrandTextStyles.bodyLarge.body(),
        color = RusuhColors.BrandInk,
    )
    Spacer(Modifier.height(RusuhSpacing.xl))
}

@Composable
private fun EmailStep(
    state: SignInUiState.Email,
    actions: SignInActions,
) {
    val canSend = isPlausibleEmail(state.email)
    SheetHeader(
        headline = stringResource(Res.string.sign_in_email_headline),
        body = stringResource(Res.string.sign_in_email_body),
    )
    BrutalTextField(
        value = state.email,
        onValueChange = actions.onEmailChange,
        placeholder = stringResource(Res.string.sign_in_email_placeholder),
        label = stringResource(Res.string.sign_in_email_label),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (canSend) actions.onSendCode() }),
    )
    Spacer(Modifier.height(RusuhSpacing.lg))
    Text(
        text = stringResource(Res.string.sign_in_email_notice),
        style = RusuhBrandTextStyles.note.body(),
        color = RusuhColors.BrandMuted,
    )
    Spacer(Modifier.height(RusuhSpacing.xxl))
    ActionRow(
        primaryText = stringResource(Res.string.sign_in_send_code),
        primaryEnabled = canSend,
        onPrimary = actions.onSendCode,
        secondaryText = stringResource(Res.string.sign_in_dismiss),
        onSecondary = actions.onDismiss,
    )
}

@Composable
private fun CodeStep(
    email: String,
    code: String,
    resendInSeconds: Int,
    attemptsLeft: Int?,
    actions: SignInActions,
) {
    val wrong = attemptsLeft != null
    SheetHeader(
        headline =
            stringResource(
                if (wrong) Res.string.sign_in_wrong_headline else Res.string.sign_in_code_headline,
            ),
        body = stringResource(Res.string.sign_in_code_body, email),
    )
    val attemptsMessage =
        attemptsLeft?.let { pluralStringResource(Res.plurals.sign_in_wrong_attempts, it, it) }
    OtpCodeInput(
        value = code,
        onValueChange = actions.onCodeChange,
        onComplete = actions.onCodeComplete,
        state = if (wrong) OtpCodeState.Error else OtpCodeState.Default,
        errorMessage = attemptsMessage,
    )
    if (attemptsMessage != null) {
        Spacer(Modifier.height(RusuhSpacing.lg))
        Text(
            text = attemptsMessage,
            style = RusuhBrandTextStyles.label.body(),
            color = RusuhColors.BrandDanger,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Spacer(Modifier.height(RusuhSpacing.xl))
    ResendRow(resendInSeconds, actions)
}

@Composable
private fun ResendRow(
    resendInSeconds: Int,
    actions: SignInActions,
) {
    val waiting = resendInSeconds > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (waiting) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.heightIn(min = RusuhSpacing.actionMinHeight),
            ) {
                Text(
                    text = stringResource(Res.string.sign_in_resend_wait, formatResendCountdown(resendInSeconds)),
                    style = RusuhBrandTextStyles.label.body(),
                    color = RusuhColors.BrandSubtle,
                )
            }
        } else {
            LinkText(text = stringResource(Res.string.sign_in_resend), onClick = actions.onResendCode)
        }
        LinkText(text = stringResource(Res.string.sign_in_change_email), onClick = actions.onChangeEmail)
    }
}

@Composable
private fun ExpiredStep(actions: SignInActions) {
    SheetHeader(
        headline = stringResource(Res.string.sign_in_expired_headline),
        body = stringResource(Res.string.sign_in_expired_body),
    )
    OtpCodeInput(value = "", onValueChange = {}, state = OtpCodeState.Disabled)
    Spacer(Modifier.height(RusuhSpacing.xxl))
    ActionRow(
        primaryText = stringResource(Res.string.sign_in_resend),
        primaryEnabled = true,
        onPrimary = actions.onResendCode,
        secondaryText = stringResource(Res.string.sign_in_change_email),
        onSecondary = actions.onChangeEmail,
    )
}

@Composable
private fun NewAccountStep(
    state: SignInUiState.NewAccount,
    actions: SignInActions,
) {
    val canCreate = state.fullName.isNotBlank() && state.consentAccepted
    SheetHeader(
        headline = stringResource(Res.string.sign_in_new_headline),
        body = stringResource(Res.string.sign_in_new_body, state.email),
    )
    BrutalTextField(
        value = state.fullName,
        onValueChange = actions.onFullNameChange,
        placeholder = stringResource(Res.string.sign_in_name_placeholder),
        label = stringResource(Res.string.sign_in_name_label),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
    Spacer(Modifier.height(RusuhSpacing.lg))
    ConsentRow(
        checked = state.consentAccepted,
        text = stringResource(Res.string.sign_in_consent),
        onCheckedChange = actions.onConsentChange,
    )
    Text(
        text = stringResource(Res.string.sign_in_account_notice),
        style = RusuhBrandTextStyles.note.body(),
        color = RusuhColors.BrandMuted,
    )
    Spacer(Modifier.height(RusuhSpacing.xxl))
    ActionRow(
        primaryText = stringResource(Res.string.sign_in_create_account),
        primaryEnabled = canCreate,
        onPrimary = actions.onCreateAccount,
        secondaryText = stringResource(Res.string.sign_in_change_email),
        onSecondary = actions.onChangeEmail,
    )
}

@Composable
private fun ActionRow(
    primaryText: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrutalButton(
            text = primaryText,
            onClick = onPrimary,
            enabled = primaryEnabled,
            // Room for the hard shadow.
            modifier = Modifier.padding(end = RusuhSpacing.md, bottom = RusuhSpacing.md),
        )
        Spacer(Modifier.size(RusuhSpacing.lg))
        LinkText(text = secondaryText, onClick = onSecondary)
    }
}

@Composable
private fun LinkText(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .heightIn(min = RusuhSpacing.actionMinHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = RusuhSpacing.md),
    ) {
        Text(
            text = text,
            style = RusuhBrandTextStyles.subtitle.body(),
            color = RusuhColors.BrandInk,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun ConsentRow(
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
