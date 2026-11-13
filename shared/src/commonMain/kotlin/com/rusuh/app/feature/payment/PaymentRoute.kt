package com.rusuh.app.feature.payment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Route-level composable for the Payment screen that wires the ViewModel to the stateless screen.
 */
@Composable
fun PaymentRoute(
    viewModel: PaymentViewModel,
    launcher: PaymentPageLauncher,
    onBack: () -> Unit,
    onNavigateToTickets: () -> Unit,
    onNavigateToHome: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

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
                    launcher.launch(effect.url)
                    // After launching Snap, the user will return via App Link.
                    // For now, we can simulate the return by dispatching ReturnedFromSnap
                    // In a real app, this would be triggered by the App Link handler.
                    viewModel.dispatch(PaymentIntent.ReturnedFromSnap)
                }
                is PaymentEffect.NavigateToETicket -> onNavigateToTickets()
                PaymentEffect.NavigateToTickets -> onNavigateToTickets()
                is PaymentEffect.NavigateToQueue -> onNavigateToHome()
                PaymentEffect.NavigateToHome -> onNavigateToHome()
            }
        }
    }

    PaymentScreen(
        state = state,
        onBack = { viewModel.dispatch(PaymentIntent.Back) },
        onOpenSnap = { viewModel.dispatch(PaymentIntent.OpenSnap) },
        onRetrySnap = { viewModel.dispatch(PaymentIntent.RetrySnap) },
        onRetry = { viewModel.dispatch(PaymentIntent.Retry) },
        onGoToTickets = { viewModel.dispatch(PaymentIntent.GoToTickets) },
        onQueueAgain = { viewModel.dispatch(PaymentIntent.QueueAgain) },
        onGoHome = { viewModel.dispatch(PaymentIntent.GoHome) },
    )
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
