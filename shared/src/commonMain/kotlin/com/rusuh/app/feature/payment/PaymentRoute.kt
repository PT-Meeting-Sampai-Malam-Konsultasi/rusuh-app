package com.rusuh.app.feature.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.rusuh.app.ui.PlatformBackHandler
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.body
import com.rusuh.app.ui.theme.display
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.action_back
import rusuh_app.shared.generated.resources.payment_close
import rusuh_app.shared.generated.resources.payment_external_missing
import rusuh_app.shared.generated.resources.payment_leave_body
import rusuh_app.shared.generated.resources.payment_leave_title
import rusuh_app.shared.generated.resources.payment_retry
import rusuh_app.shared.generated.resources.payment_title
import rusuh_app.shared.generated.resources.payment_web_error
import kotlin.time.Duration.Companion.seconds

/**
 * Route-level composable for the Payment screen that wires the ViewModel to the stateless screen.
 */
@Composable
@Suppress("CyclomaticComplexMethod")
fun PaymentRoute(
    viewModel: PaymentViewModel,
    bookingId: String,
    onBack: () -> Unit,
    onNavigateToTickets: () -> Unit,
    onNavigateToHome: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var snapUrl by remember { mutableStateOf<String?>(null) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf(false) }
    var webLoading by remember { mutableStateOf(true) }

    PlatformBackHandler(enabled = snapUrl != null) {
        showLeaveDialog = true
    }

    LaunchedEffect(state.ui) {
        if (state.ui is PaymentUiState.Expired) snapUrl = null
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.dispatch(PaymentIntent.Resumed)
    }

    // Initial load
    LaunchedEffect(viewModel) {
        viewModel.dispatch(PaymentIntent.Load)
    }

    // Countdown ticker
    LaunchedEffect(state.ui) {
        // Only tick when we're in a state that shows a countdown
        while (state.ui is PaymentUiState.Ready || state.ui is PaymentUiState.Verifying) {
            delay(1.seconds)
            viewModel.dispatch(PaymentIntent.Tick)
        }
    }

    // Effect handler
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PaymentEffect.NavigateBack -> onBack()
                is PaymentEffect.LaunchSnap -> {
                    snapUrl = effect.url
                    loadError = false
                }
                is PaymentEffect.NavigateToETicket -> onNavigateToTickets()
                PaymentEffect.NavigateToTickets -> onNavigateToTickets()
                is PaymentEffect.NavigateToQueue -> onNavigateToHome()
                PaymentEffect.NavigateToHome -> onNavigateToHome()
            }
        }
    }

    if (snapUrl == null) {
        PaymentScreen(
            state = state,
            onBack = { viewModel.dispatch(PaymentIntent.Back) },
            onOpenSnap = { viewModel.dispatch(PaymentIntent.OpenSnap) },
            onRetrySnap = { viewModel.dispatch(PaymentIntent.RetrySnap) },
            onRetry = { viewModel.dispatch(PaymentIntent.Retry) },
            onGoToTickets = { viewModel.dispatch(PaymentIntent.GoToTickets) },
            onQueueAgain = { viewModel.dispatch(PaymentIntent.QueueAgain) },
            onGoHome = { viewModel.dispatch(PaymentIntent.GoHome) },
            onCancelBooking = { viewModel.dispatch(PaymentIntent.CancelBooking) },
        )
    } else {
        Column(Modifier.fillMaxSize().background(RusuhColors.BrandPaper)) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showLeaveDialog = true }) { Text(stringResource(Res.string.action_back)) }
                Text(
                    stringResource(Res.string.payment_title),
                    Modifier.weight(1f),
                    style = RusuhBrandTextStyles.itemTitle.display(),
                    color = RusuhColors.BrandInk,
                )
                if (webLoading && !loadError) CircularProgressIndicator(color = RusuhColors.BrandMagenta)
                TextButton(onClick = {
                    snapUrl = null
                    viewModel.dispatch(PaymentIntent.SnapClosed)
                }) { Text(stringResource(Res.string.payment_close)) }
            }
            if (loadError) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(Res.string.payment_web_error), style = RusuhBrandTextStyles.bodyMedium.body())
                    TextButton(onClick = { loadError = false }) { Text(stringResource(Res.string.payment_retry)) }
                    TextButton(onClick = {
                        snapUrl = null
                        viewModel.dispatch(PaymentIntent.SnapClosed)
                    }) { Text(stringResource(Res.string.payment_close)) }
                }
            } else {
                Box(Modifier.weight(1f)) {
                    PaymentSnapWebView(
                        url = snapUrl!!,
                        bookingId = bookingId,
                        modifier = Modifier.fillMaxSize(),
                        onClose = {
                            snapUrl = null
                            viewModel.dispatch(PaymentIntent.SnapClosed)
                        },
                        onLoading = { webLoading = it },
                        onLoadError = {
                            loadError = true
                            webLoading = false
                        },
                        onNotice = { notice = true },
                    )
                }
            }
        }
        if (showLeaveDialog) {
            AlertDialog(
                onDismissRequest = { showLeaveDialog = false },
                title = { Text(stringResource(Res.string.payment_leave_title)) },
                text = { Text(stringResource(Res.string.payment_leave_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        showLeaveDialog = false
                        snapUrl = null
                        viewModel.dispatch(PaymentIntent.SnapClosed)
                    }) { Text(stringResource(Res.string.payment_close)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showLeaveDialog = false },
                    ) { Text(stringResource(Res.string.action_back)) }
                },
            )
        }
        if (notice) {
            AlertDialog(
                onDismissRequest = { notice = false },
                text = { Text(stringResource(Res.string.payment_external_missing)) },
                confirmButton = {
                    TextButton(
                        onClick = { notice = false },
                    ) { Text(stringResource(Res.string.action_back)) }
                },
            )
        }
    }
}

/**
 * Route-level composable for the Payment Result screen (App Link entry point).
 */
@Composable
fun PaymentResultRoute(
    viewModel: PaymentResultViewModel,
    onNavigateToTickets: () -> Unit,
    onNavigateToHome: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    // Initial load
    LaunchedEffect(viewModel) {
        viewModel.dispatch(PaymentResultIntent.Load)
    }

    // Effect handler
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PaymentResultEffect.NavigateToTickets -> onNavigateToTickets()
                PaymentResultEffect.NavigateToHome -> onNavigateToHome()
            }
        }
    }

    // Reuse the payment screen for displaying the result states
    when (val ui = state.ui) {
        is PaymentResultUiState.Loading -> {
            PaymentScreen(
                state = PaymentContract.State(ui = PaymentUiState.Loading),
                onBack = {},
                onOpenSnap = {},
                onRetrySnap = {},
                onRetry = {},
                onGoToTickets = {},
                onQueueAgain = {},
                onGoHome = {},
            )
        }
        is PaymentResultUiState.Success -> {
            PaymentScreen(
                state =
                    PaymentContract.State(
                        ui =
                            PaymentUiState.Success(
                                orderCode = ui.orderCode,
                                eventName = ui.eventName,
                                ticketCount = ui.ticketCount,
                                bookingId = ui.bookingId,
                            ),
                    ),
                onBack = {},
                onOpenSnap = {},
                onRetrySnap = {},
                onRetry = {},
                onGoToTickets = { viewModel.dispatch(PaymentResultIntent.GoToTickets) },
                onQueueAgain = {},
                onGoHome = {},
            )
        }
        is PaymentResultUiState.Expired -> {
            // Use a minimal summary for the expired state
            PaymentScreen(
                state =
                    PaymentContract.State(
                        ui =
                            PaymentUiState.Expired(
                                summary =
                                    PaymentSummaryUi(
                                        bookingId = "",
                                        orderCode = ui.orderCode ?: "",
                                        eventName = null,
                                        eventMeta = null,
                                        attendees = emptyList(),
                                        price = PriceBreakdownUi(0, 0, null, 0),
                                        customerEmail = ui.customerEmail,
                                    ),
                                customerEmail = ui.customerEmail,
                                refundRequired = ui.refundRequired,
                            ),
                    ),
                onBack = {},
                onOpenSnap = {},
                onRetrySnap = {},
                onRetry = {},
                onGoToTickets = {},
                onQueueAgain = { viewModel.dispatch(PaymentResultIntent.QueueAgain) },
                onGoHome = { viewModel.dispatch(PaymentResultIntent.GoHome) },
            )
        }
        is PaymentResultUiState.Pending -> {
            // User closed Snap before completing - show loading
            PaymentScreen(
                state = PaymentContract.State(ui = PaymentUiState.Loading),
                onBack = {},
                onOpenSnap = {},
                onRetrySnap = {},
                onRetry = {},
                onGoToTickets = {},
                onQueueAgain = {},
                onGoHome = {},
            )
        }
        is PaymentResultUiState.Error -> {
            PaymentScreen(
                state = PaymentContract.State(ui = PaymentUiState.Error(ui.message)),
                onBack = {},
                onOpenSnap = {},
                onRetrySnap = {},
                onRetry = { viewModel.dispatch(PaymentResultIntent.Retry) },
                onGoToTickets = {},
                onQueueAgain = {},
                onGoHome = { viewModel.dispatch(PaymentResultIntent.GoHome) },
            )
        }
    }
}
