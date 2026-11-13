@file:Suppress("TooManyFunctions") // UI composables for different screen states

package com.rusuh.app.feature.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rusuh.app.ui.components.BrutalButton
import com.rusuh.app.ui.components.formatRupiah
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import com.rusuh.app.ui.theme.display
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.action_back
import rusuh_app.shared.generated.resources.action_retry
import rusuh_app.shared.generated.resources.payment_admin_fee
import rusuh_app.shared.generated.resources.payment_busy_error
import rusuh_app.shared.generated.resources.payment_cancel_failed
import rusuh_app.shared.generated.resources.payment_cancel_not_cancellable
import rusuh_app.shared.generated.resources.payment_cancel_order
import rusuh_app.shared.generated.resources.payment_cancelling
import rusuh_app.shared.generated.resources.payment_continue_button
import rusuh_app.shared.generated.resources.payment_error_load
import rusuh_app.shared.generated.resources.payment_expired_badge
import rusuh_app.shared.generated.resources.payment_expired_body
import rusuh_app.shared.generated.resources.payment_expired_headline
import rusuh_app.shared.generated.resources.payment_expired_home
import rusuh_app.shared.generated.resources.payment_expired_queue_again
import rusuh_app.shared.generated.resources.payment_expired_refund_notice
import rusuh_app.shared.generated.resources.payment_loading
import rusuh_app.shared.generated.resources.payment_midtrans_hint
import rusuh_app.shared.generated.resources.payment_not_paid_badge
import rusuh_app.shared.generated.resources.payment_order_section
import rusuh_app.shared.generated.resources.payment_pay_via_midtrans
import rusuh_app.shared.generated.resources.payment_snap_closed_body
import rusuh_app.shared.generated.resources.payment_snap_closed_headline
import rusuh_app.shared.generated.resources.payment_success_body
import rusuh_app.shared.generated.resources.payment_success_cta
import rusuh_app.shared.generated.resources.payment_success_headline
import rusuh_app.shared.generated.resources.payment_time_hint
import rusuh_app.shared.generated.resources.payment_title
import rusuh_app.shared.generated.resources.payment_total
import rusuh_app.shared.generated.resources.payment_verifying_body
import rusuh_app.shared.generated.resources.payment_verifying_headline
import rusuh_app.shared.generated.resources.payment_verifying_note

/**
 * Payment screen composable. Stateless: receives state and dispatches actions.
 */
@Suppress("LongParameterList") // Compose screen with multiple callback handlers
@Composable
fun PaymentScreen(
    state: PaymentContract.State,
    onBack: () -> Unit,
    onOpenSnap: () -> Unit,
    onRetrySnap: () -> Unit,
    onRetry: () -> Unit,
    onGoToTickets: () -> Unit,
    onQueueAgain: () -> Unit,
    onGoHome: () -> Unit,
    onCancelBooking: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(RusuhColors.BrandPaper),
    ) {
        // Top bar with timer (unless expired/success)
        val showTimer = state.ui !is PaymentUiState.Success && state.ui !is PaymentUiState.Expired
        PaymentTopBar(
            remainingSeconds = if (showTimer) state.remainingSeconds else null,
            onBack = onBack,
        )

        when (val ui = state.ui) {
            is PaymentUiState.Loading -> PaymentLoadingContent()
            is PaymentUiState.Ready ->
                PaymentReadyContent(
                    ui = ui,
                    isLaunching = state.isSnapLaunching,
                    isCancelling = state.isCancelling,
                    cancelError = state.cancelError,
                    onOpenSnap = onOpenSnap,
                    onRetrySnap = onRetrySnap,
                    onCancelBooking = onCancelBooking,
                )
            is PaymentUiState.Verifying -> PaymentVerifyingContent()
            is PaymentUiState.Success -> PaymentSuccessContent(onGoToTickets = onGoToTickets)
            is PaymentUiState.Expired ->
                PaymentExpiredContent(
                    ui = ui,
                    onQueueAgain = onQueueAgain,
                    onGoHome = onGoHome,
                )
            is PaymentUiState.Error -> PaymentErrorContent(message = ui.message, onRetry = onRetry)
        }
    }
}

@Composable
private fun PaymentTopBar(
    remainingSeconds: Int?,
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(RusuhSpacing.topBarHeight)
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val backDescription = stringResource(Res.string.action_back)
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(RusuhSpacing.topBarHeight)
                    .semantics { contentDescription = backDescription }
                    .clickable(role = Role.Button, onClick = onBack),
        ) {
            Text(
                text = "←",
                style = RusuhBrandTextStyles.itemTitle.display(),
                color = RusuhColors.BrandInk,
            )
        }
        Text(
            text = stringResource(Res.string.payment_title),
            style = RusuhBrandTextStyles.itemTitle.display(),
            color = RusuhColors.BrandInk,
            modifier = Modifier.weight(1f),
        )
        if (remainingSeconds != null) {
            val formattedTime = formatPaymentCountdown(remainingSeconds)
            val isWarning = remainingSeconds <= PAYMENT_COUNTDOWN_WARN_SECONDS
            val bgColor = if (isWarning) RusuhColors.BrandDanger else RusuhColors.BrandMagenta
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor)
                        .border(2.dp, RusuhColors.BrandInk, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .semantics {
                            contentDescription = "Time left $formattedTime"
                        },
            ) {
                Text(
                    text = formattedTime,
                    style = RusuhBrandTextStyles.label.body(),
                    fontWeight = FontWeight.Bold,
                    color = RusuhColors.BrandPaper,
                )
            }
        }
    }
}

@Composable
private fun PaymentLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = RusuhColors.BrandMagenta,
                strokeWidth = 4.dp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.payment_loading),
                style = RusuhBrandTextStyles.bodyMedium.body(),
                color = RusuhColors.BrandMuted,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun PaymentReadyContent(
    ui: PaymentUiState.Ready,
    isLaunching: Boolean,
    isCancelling: Boolean,
    cancelError: CancelError?,
    onOpenSnap: () -> Unit,
    onRetrySnap: () -> Unit,
    onCancelBooking: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        // Section header
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(RusuhColors.BrandInk)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "2",
                    style = RusuhBrandTextStyles.label.body(),
                    color = RusuhColors.BrandPaper,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.payment_pay_via_midtrans),
                style = RusuhBrandTextStyles.subtitle.display(),
                color = RusuhColors.BrandInk,
            )
        }

        Text(
            text = stringResource(Res.string.payment_time_hint),
            style = RusuhBrandTextStyles.note.body(),
            color = RusuhColors.BrandMuted,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Snap closed state
        if (ui.hasOpenedSnap) {
            SnapClosedCard()
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Busy error
        if (ui.busyError != null) {
            BusyErrorCard(onRetry = onRetrySnap)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Order summary
        OrderSummaryCard(summary = ui.summary)

        if (cancelError != null) {
            Text(
                text =
                    stringResource(
                        if (cancelError == CancelError.NOT_CANCELLABLE) {
                            Res.string.payment_cancel_not_cancellable
                        } else {
                            Res.string.payment_cancel_failed
                        },
                    ),
                color = RusuhColors.BrandDanger,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // CTA button
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            BrutalButton(
                text = stringResource(Res.string.payment_continue_button),
                onClick = onOpenSnap,
                enabled = !isLaunching && ui.busyError == null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = stringResource(if (isCancelling) Res.string.payment_cancelling else Res.string.payment_cancel_order),
            color = RusuhColors.BrandDanger,
            modifier =
                Modifier.align(Alignment.CenterHorizontally).clickable(
                    enabled = !isCancelling && !isLaunching,
                    role = Role.Button,
                    onClick = onCancelBooking,
                ).padding(16.dp),
        )
    }
}

@Composable
private fun SnapClosedCard() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, RusuhColors.BrandInk, RoundedCornerShape(12.dp))
                .background(RusuhColors.BrandCream)
                .padding(16.dp),
    ) {
        // Badge
        Box(
            modifier =
                Modifier
                    .border(2.dp, RusuhColors.BrandInk, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(Res.string.payment_not_paid_badge),
                style = RusuhBrandTextStyles.eyebrow.body(),
                color = RusuhColors.BrandInk,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.payment_snap_closed_headline),
            style = RusuhBrandTextStyles.subtitle.display(),
            color = RusuhColors.BrandInk,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.payment_snap_closed_body),
            style = RusuhBrandTextStyles.bodyMedium.body(),
            color = RusuhColors.BrandMuted,
        )
    }
}

@Composable
private fun BusyErrorCard(onRetry: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, RusuhColors.BrandDanger, RoundedCornerShape(12.dp))
                .background(RusuhColors.BrandDangerSurface)
                .padding(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.payment_busy_error),
            style = RusuhBrandTextStyles.bodyMedium.body(),
            color = RusuhColors.BrandDanger,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SecondaryButton(
            text = stringResource(Res.string.action_retry),
            onClick = onRetry,
        )
    }
}

@Composable
private fun OrderSummaryCard(summary: PaymentSummaryUi) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, RusuhColors.BrandInk, RoundedCornerShape(12.dp))
                .background(RusuhColors.BrandCream)
                .padding(16.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.payment_order_section),
                style = RusuhBrandTextStyles.label.display(),
                color = RusuhColors.BrandInk,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = summary.orderCode,
                style = RusuhBrandTextStyles.eyebrow.body(),
                color = RusuhColors.BrandMuted,
            )
        }
        summary.eventMeta?.let { meta ->
            Text(
                text = meta,
                style = RusuhBrandTextStyles.note.body(),
                color = RusuhColors.BrandMuted,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = RusuhColors.BrandHairline, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))

        // Attendees
        summary.attendees.forEach { attendee ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = attendee.categoryName,
                        style = RusuhBrandTextStyles.bodyMedium.body(),
                        color = RusuhColors.BrandInk,
                    )
                    Text(
                        text = attendee.nikMasked,
                        style = RusuhBrandTextStyles.note.body(),
                        color = RusuhColors.BrandMuted,
                    )
                }
                Text(
                    text = formatRupiah(attendee.priceRupiah),
                    style = RusuhBrandTextStyles.bodyMedium.body(),
                    color = RusuhColors.BrandInk,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        HorizontalDivider(color = RusuhColors.BrandHairline, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        // Admin fee
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.payment_admin_fee),
                style = RusuhBrandTextStyles.bodyMedium.body(),
                color = RusuhColors.BrandMuted,
            )
            Text(
                text = formatRupiah(summary.price.adminFee),
                style = RusuhBrandTextStyles.bodyMedium.body(),
                color = RusuhColors.BrandMuted,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = RusuhColors.BrandInk, thickness = 2.dp)
        Spacer(modifier = Modifier.height(8.dp))

        // Total
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.payment_total),
                style = RusuhBrandTextStyles.subtitle.display(),
                color = RusuhColors.BrandInk,
            )
            Text(
                text = formatRupiah(summary.price.total),
                style = RusuhBrandTextStyles.subtitle.display(),
                fontWeight = FontWeight.Bold,
                color = RusuhColors.BrandInk,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Midtrans hint
        Text(
            text = stringResource(Res.string.payment_midtrans_hint),
            style = RusuhBrandTextStyles.note.body(),
            color = RusuhColors.BrandMuted,
        )
    }
}

@Composable
private fun PaymentVerifyingContent() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = RusuhColors.BrandMagenta,
            strokeWidth = 6.dp,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.payment_verifying_headline),
            style = RusuhBrandTextStyles.itemTitle.display(),
            color = RusuhColors.BrandInk,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.payment_verifying_body),
            style = RusuhBrandTextStyles.bodyMedium.body(),
            color = RusuhColors.BrandMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.payment_verifying_note),
            style = RusuhBrandTextStyles.note.body(),
            color = RusuhColors.BrandMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PaymentSuccessContent(onGoToTickets: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Success badge
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(RusuhColors.Green)
                    .border(2.dp, RusuhColors.BrandInk, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "✓ PAID",
                style = RusuhBrandTextStyles.label.body(),
                fontWeight = FontWeight.Bold,
                color = RusuhColors.BrandPaper,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.payment_success_headline),
            style = RusuhBrandTextStyles.sectionTitle.display(),
            color = RusuhColors.BrandInk,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.payment_success_body),
            style = RusuhBrandTextStyles.bodyMedium.body(),
            color = RusuhColors.BrandMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        BrutalButton(
            text = stringResource(Res.string.payment_success_cta),
            onClick = onGoToTickets,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PaymentExpiredContent(
    ui: PaymentUiState.Expired,
    onQueueAgain: () -> Unit,
    onGoHome: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Expired badge
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(RusuhColors.BrandInk)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.payment_expired_badge),
                style = RusuhBrandTextStyles.label.body(),
                fontWeight = FontWeight.Bold,
                color = RusuhColors.BrandPaper,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.payment_expired_headline),
            style = RusuhBrandTextStyles.sectionTitle.display(),
            color = RusuhColors.BrandInk,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.payment_expired_body),
            style = RusuhBrandTextStyles.bodyMedium.body(),
            color = RusuhColors.BrandMuted,
            textAlign = TextAlign.Center,
        )

        // Refund notice if payment arrived late (ADR-0009)
        if (ui.refundRequired && ui.customerEmail != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, RusuhColors.BrandHairline, RoundedCornerShape(12.dp))
                        .padding(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.payment_expired_refund_notice, ui.customerEmail),
                    style = RusuhBrandTextStyles.note.body(),
                    color = RusuhColors.BrandMuted,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        BrutalButton(
            text = stringResource(Res.string.payment_expired_queue_again),
            onClick = onQueueAgain,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            text = stringResource(Res.string.payment_expired_home),
            onClick = onGoHome,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Order summary
        OrderSummaryCard(summary = ui.summary)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PaymentErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.payment_error_load),
            style = RusuhBrandTextStyles.subtitle.display(),
            color = RusuhColors.BrandInk,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = RusuhBrandTextStyles.bodyMedium.body(),
            color = RusuhColors.BrandMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        SecondaryButton(
            text = stringResource(Res.string.action_retry),
            onClick = onRetry,
        )
    }
}

/**
 * Secondary button: ink border, paper fill, no shadow. Used for retry actions.
 */
@Composable
private fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .heightIn(min = RusuhSpacing.ctaHeight)
                .border(2.dp, RusuhColors.BrandInk)
                .background(RusuhColors.BrandPaper)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = RusuhSpacing.xl),
    ) {
        Text(
            text = text,
            style = RusuhBrandTextStyles.cta.display(),
            color = RusuhColors.BrandInk,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Text-only button for tertiary actions. Used for "Go home" on expired state.
 */
@Composable
private fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = RusuhSpacing.xl, vertical = RusuhSpacing.md),
    ) {
        Text(
            text = text,
            style = RusuhBrandTextStyles.cta.display(),
            color = RusuhColors.BrandMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** Formats a rupiah amount with thousand separators. */
