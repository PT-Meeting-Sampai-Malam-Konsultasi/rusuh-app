package com.rusuh.app.feature.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.rusuh.app.ui.components.BrutalTextField
import com.rusuh.app.ui.components.OtpCodeInput
import com.rusuh.app.ui.components.OtpCodeState
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.sign_in_badge
import rusuh_app.shared.generated.resources.sign_in_change_email
import rusuh_app.shared.generated.resources.sign_in_code_headline
import rusuh_app.shared.generated.resources.sign_in_code_hint
import rusuh_app.shared.generated.resources.sign_in_code_label
import rusuh_app.shared.generated.resources.sign_in_code_sent_to
import rusuh_app.shared.generated.resources.sign_in_consent
import rusuh_app.shared.generated.resources.sign_in_continue
import rusuh_app.shared.generated.resources.sign_in_continue_queue
import rusuh_app.shared.generated.resources.sign_in_email_body
import rusuh_app.shared.generated.resources.sign_in_email_headline
import rusuh_app.shared.generated.resources.sign_in_email_hint
import rusuh_app.shared.generated.resources.sign_in_email_label
import rusuh_app.shared.generated.resources.sign_in_email_placeholder
import rusuh_app.shared.generated.resources.sign_in_error_invalid
import rusuh_app.shared.generated.resources.sign_in_error_network
import rusuh_app.shared.generated.resources.sign_in_error_rate_limited
import rusuh_app.shared.generated.resources.sign_in_error_rate_limited_now
import rusuh_app.shared.generated.resources.sign_in_error_unknown
import rusuh_app.shared.generated.resources.sign_in_expired_error
import rusuh_app.shared.generated.resources.sign_in_expired_headline
import rusuh_app.shared.generated.resources.sign_in_name_hint
import rusuh_app.shared.generated.resources.sign_in_name_label
import rusuh_app.shared.generated.resources.sign_in_name_placeholder
import rusuh_app.shared.generated.resources.sign_in_new_body
import rusuh_app.shared.generated.resources.sign_in_new_headline
import rusuh_app.shared.generated.resources.sign_in_privacy_link
import rusuh_app.shared.generated.resources.sign_in_resend
import rusuh_app.shared.generated.resources.sign_in_resend_wait
import rusuh_app.shared.generated.resources.sign_in_send_code
import rusuh_app.shared.generated.resources.sign_in_send_new_code
import rusuh_app.shared.generated.resources.sign_in_terms
import rusuh_app.shared.generated.resources.sign_in_terms_link
import rusuh_app.shared.generated.resources.sign_in_verify
import rusuh_app.shared.generated.resources.sign_in_wrong_attempts

private const val BADGE_TILT_DEGREES = -2f
private const val OTP_GROUP_SIZE = 3

/**
 * The part of the login shared by the full screen (#101) and the Account tab (#102): title,
 * subtitle, fields, main button, hints and terms for one [SignInUiState] step. Stateless; every
 * edit goes through [actions]. The full variant adds its top bar, event banner and info blocks
 * around it.
 *
 * [status] adds the in-flight state (the main button is disabled) and one inline error line above the main button.
 *
 * [autoFocusEmail] focuses the email field on entry (wanted on the full screen, not on a tab
 * the user merely opened). Code and name steps always take focus because the user just got there.
 */
@Suppress("LongParameterList")
@Composable
fun SignInContent(
    state: SignInUiState,
    actions: SignInActions,
    variant: SignInVariant,
    modifier: Modifier = Modifier,
    autoFocusEmail: Boolean = false,
    status: SignInStatus = SignInStatus(),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when (state) {
            is SignInUiState.Email -> EmailStep(state, actions, autoFocusEmail, status)
            // One call site for both, so the input (and its focus and keyboard) is kept
            // when a wrong code arrives.
            is SignInUiState.Code, is SignInUiState.WrongCode -> CodeStep(state, actions, status)
            is SignInUiState.CodeExpired -> ExpiredStep(state, actions, status)
            is SignInUiState.NewAccount -> NewAccountStep(state, actions, variant, status)
        }
    }
}

@Composable
private fun EmailStep(
    state: SignInUiState.Email,
    actions: SignInActions,
    autoFocus: Boolean,
    status: SignInStatus,
) {
    val canSend = state.isPrimaryEnabled() && !status.blocksPrimary
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    SignInHeadline(stringResource(Res.string.sign_in_email_headline))
    Spacer(Modifier.height(RusuhSpacing.lg))
    SignInSubtitle(stringResource(Res.string.sign_in_email_body))
    Spacer(Modifier.height(RusuhSpacing.xxl))
    FieldLabel(stringResource(Res.string.sign_in_email_label))
    Spacer(Modifier.height(RusuhSpacing.md))
    BrutalTextField(
        value = state.email,
        onValueChange = actions.onEmailChange,
        placeholder = stringResource(Res.string.sign_in_email_placeholder),
        label = stringResource(Res.string.sign_in_email_label),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (canSend) actions.onSendCode() }),
        readOnly = status.isBusy,
        modifier = Modifier.focusRequester(focusRequester),
    )
    Spacer(Modifier.height(RusuhSpacing.lg))
    StatusLine(status)
    SignInPrimaryButton(stringResource(Res.string.sign_in_send_code), canSend, actions.onSendCode)
    Spacer(Modifier.height(RusuhSpacing.md))
    HintText(stringResource(Res.string.sign_in_email_hint))
    Spacer(Modifier.height(RusuhSpacing.xl))
    // Terms opens the website page; the privacy link stays inert until its page exists.
    val terms = stringResource(Res.string.sign_in_terms_link)
    val privacy = stringResource(Res.string.sign_in_privacy_link)
    HintText(
        text = stringResource(Res.string.sign_in_terms, terms, privacy),
        underline = listOf(terms, privacy),
        links = mapOf(terms to LegalLinks.TERMS_URL),
    )
}

@Composable
private fun CodeStep(
    state: SignInUiState,
    actions: SignInActions,
    status: SignInStatus,
) {
    val email: String
    val code: String
    val resendInSeconds: Int
    val validForSeconds: Int
    val attemptsLeft: Int?
    when (state) {
        is SignInUiState.Code -> {
            email = state.email
            code = state.code
            resendInSeconds = state.resendInSeconds
            validForSeconds = state.validForSeconds
            attemptsLeft = null
        }
        is SignInUiState.WrongCode -> {
            email = state.email
            code = state.code
            resendInSeconds = state.resendInSeconds
            validForSeconds = state.validForSeconds
            attemptsLeft = state.attemptsLeft
        }
        else -> return
    }
    val canSubmit = state.isPrimaryEnabled() && !status.blocksPrimary
    SignInHeadline(stringResource(Res.string.sign_in_code_headline))
    Spacer(Modifier.height(RusuhSpacing.lg))
    SentTo(email, actions)
    Spacer(Modifier.height(RusuhSpacing.xl))
    FieldLabel(stringResource(Res.string.sign_in_code_label))
    Spacer(Modifier.height(RusuhSpacing.md))
    val wrongMessage =
        attemptsLeft?.let {
            pluralStringResource(Res.plurals.sign_in_wrong_attempts, it, it, SignInPolicy.LOCKOUT_MINUTES)
        }
    OtpCodeInput(
        value = code,
        onValueChange = actions.onCodeChange,
        onComplete = actions.onCodeComplete,
        state = if (wrongMessage != null) OtpCodeState.Error else OtpCodeState.Default,
        errorMessage = wrongMessage,
        autoFocus = true,
        separatorAfter = OTP_GROUP_SIZE,
    )
    if (wrongMessage != null) {
        Spacer(Modifier.height(RusuhSpacing.md))
        ErrorLine(wrongMessage)
    }
    Spacer(Modifier.height(RusuhSpacing.lg))
    StatusLine(status)
    SignInPrimaryButton(stringResource(Res.string.sign_in_verify), canSubmit) { actions.onCodeComplete(code) }
    ResendRow(resendInSeconds, actions, enabled = !status.isBusy)
    Spacer(Modifier.height(RusuhSpacing.md))
    val sender = SignInPolicy.SENDER_ADDRESS
    val validity = formatResendCountdown(validForSeconds)
    HintText(
        text = stringResource(Res.string.sign_in_code_hint, sender, validity),
        emphasis = listOf(sender, validity),
    )
}

/** "Kode dikirim ke <email>" with the "Ganti email" link under it. */
@Composable
private fun SentTo(
    email: String,
    actions: SignInActions,
) {
    SignInSubtitle(stringResource(Res.string.sign_in_code_sent_to, email), emphasis = listOf(email))
    LinkText(
        text = stringResource(Res.string.sign_in_change_email),
        onClick = actions.onChangeEmail,
        color = RusuhColors.BrandMuted,
        style = RusuhBrandTextStyles.bodyLarge,
    )
}

@Composable
private fun ResendRow(
    resendInSeconds: Int,
    actions: SignInActions,
    enabled: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (resendInSeconds > 0) {
            val time = formatResendCountdown(resendInSeconds)
            HintText(
                text = stringResource(Res.string.sign_in_resend_wait, time),
                emphasis = listOf(time),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = RusuhSpacing.md),
            )
        } else {
            LinkText(
                text = stringResource(Res.string.sign_in_resend),
                onClick = { if (enabled) actions.onResendCode() },
            )
        }
    }
}

@Composable
private fun ExpiredStep(
    state: SignInUiState.CodeExpired,
    actions: SignInActions,
    status: SignInStatus,
) {
    SignInHeadline(stringResource(Res.string.sign_in_expired_headline))
    Spacer(Modifier.height(RusuhSpacing.lg))
    SentTo(state.email, actions)
    Spacer(Modifier.height(RusuhSpacing.xl))
    FieldLabel(stringResource(Res.string.sign_in_code_label))
    Spacer(Modifier.height(RusuhSpacing.md))
    // The old code, disabled and struck through; hidden from a screen reader (the error says it all).
    OtpCodeInput(
        value = state.code,
        onValueChange = {},
        state = OtpCodeState.Disabled,
        separatorAfter = OTP_GROUP_SIZE,
        strikethrough = true,
        modifier = Modifier.clearAndSetSemantics {},
    )
    Spacer(Modifier.height(RusuhSpacing.md))
    ErrorLine(stringResource(Res.string.sign_in_expired_error, SignInPolicy.CODE_VALID_MINUTES))
    Spacer(Modifier.height(RusuhSpacing.lg))
    StatusLine(status)
    SignInPrimaryButton(stringResource(Res.string.sign_in_send_new_code), !status.blocksPrimary, actions.onResendCode)
}

@Composable
private fun NewAccountStep(
    state: SignInUiState.NewAccount,
    actions: SignInActions,
    variant: SignInVariant,
    status: SignInStatus,
) {
    val canContinue = state.isPrimaryEnabled() && !status.blocksPrimary
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Text(
        // Uppercase stamp, tilted like the Figma badge.
        text = stringResource(Res.string.sign_in_badge).uppercase(),
        style = RusuhBrandTextStyles.eyebrow.body(),
        color = RusuhColors.BlueInk,
        modifier =
            Modifier
                .rotate(BADGE_TILT_DEGREES)
                .border(RusuhSpacing.borderHeavy, RusuhColors.BlueInk)
                .padding(horizontal = RusuhSpacing.lg, vertical = RusuhSpacing.md),
    )
    Spacer(Modifier.height(RusuhSpacing.xl))
    SignInHeadline(stringResource(Res.string.sign_in_new_headline))
    Spacer(Modifier.height(RusuhSpacing.lg))
    SignInSubtitle(stringResource(Res.string.sign_in_new_body, state.email), emphasis = listOf(state.email))
    Spacer(Modifier.height(RusuhSpacing.xxl))
    FieldLabel(stringResource(Res.string.sign_in_name_label))
    Spacer(Modifier.height(RusuhSpacing.md))
    BrutalTextField(
        value = state.fullName,
        onValueChange = actions.onFullNameChange,
        placeholder = stringResource(Res.string.sign_in_name_placeholder),
        label = stringResource(Res.string.sign_in_name_label),
        keyboardOptions =
            KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (canContinue) actions.onCreateAccount() }),
        readOnly = status.isBusy,
        modifier = Modifier.focusRequester(focusRequester),
    )
    Spacer(Modifier.height(RusuhSpacing.md))
    HintText(stringResource(Res.string.sign_in_name_hint))
    Spacer(Modifier.height(RusuhSpacing.lg))
    ConsentRow(
        checked = state.consentAccepted,
        text = stringResource(Res.string.sign_in_consent),
        onCheckedChange = actions.onConsentChange,
    )
    Spacer(Modifier.height(RusuhSpacing.xl))
    StatusLine(status)
    SignInPrimaryButton(
        text =
            stringResource(
                if (variant == SignInVariant.Full) Res.string.sign_in_continue_queue else Res.string.sign_in_continue,
            ),
        enabled = canContinue,
        onClick = actions.onCreateAccount,
    )
}

/** The inline error of [status] (if any) with the space below it; nothing when there is none. */
@Composable
private fun StatusLine(status: SignInStatus) {
    val error = status.error ?: return
    val message =
        when (error) {
            SignInError.Network -> stringResource(Res.string.sign_in_error_network)
            is SignInError.RateLimited ->
                error.secondsLeft?.takeIf { it > 0 }?.let {
                    stringResource(Res.string.sign_in_error_rate_limited, formatResendCountdown(it))
                } ?: stringResource(Res.string.sign_in_error_rate_limited_now)
            SignInError.InvalidInput -> stringResource(Res.string.sign_in_error_invalid)
            SignInError.Unknown -> stringResource(Res.string.sign_in_error_unknown)
        }
    val announcement =
        if (error is SignInError.RateLimited) {
            stringResource(Res.string.sign_in_error_rate_limited_now)
        } else {
            message
        }
    ErrorLine(message, announceText = announcement)
    Spacer(Modifier.height(RusuhSpacing.lg))
}
