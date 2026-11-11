package com.rusuh.app.feature.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.detail_error_booking
import rusuh_app.shared.generated.resources.detail_error_load
import rusuh_app.shared.generated.resources.detail_error_nik_rejected

/** Countdown tick period. The remaining time itself comes from the server clock, not from this. */
private const val TICK_MILLIS = 1_000L

/**
 * Stateful Detail host: collects [DetailState] from the [DetailViewModel], runs the initial load,
 * ticks the countdown while the order is active and routes one-time effects to the callbacks.
 * Rendering is delegated to the stateless [DetailScreen].
 */
@Suppress("LongParameterList")
@Composable
fun DetailRoute(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onQueue: (eventId: String) -> Unit,
    onPayment: (bookingId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val errorTexts =
        mapOf(
            DetailError.LoadFailed to stringResource(Res.string.detail_error_load),
            DetailError.BookingFailed to stringResource(Res.string.detail_error_booking),
            DetailError.NikRejected to stringResource(Res.string.detail_error_nik_rejected),
        )
    val currentErrorTexts by rememberUpdatedState(errorTexts)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnHome by rememberUpdatedState(onHome)
    val currentOnQueue by rememberUpdatedState(onQueue)
    val currentOnPayment by rememberUpdatedState(onPayment)

    LaunchedEffect(viewModel) { viewModel.dispatch(DetailIntent.Load) }

    val counting = state.isCountingDown
    LaunchedEffect(viewModel, counting) {
        while (counting) {
            delay(TICK_MILLIS)
            viewModel.dispatch(DetailIntent.Tick)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                DetailEffect.NavigateBack -> currentOnBack()
                DetailEffect.NavigateHome -> currentOnHome()
                is DetailEffect.NavigateToQueue -> currentOnQueue(effect.eventId)
                is DetailEffect.NavigateToPayment -> currentOnPayment(effect.bookingId)
                // Own coroutine: showSnackbar suspends until dismissed and must never delay
                // the navigation effects queued behind it.
                is DetailEffect.ShowError ->
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(currentErrorTexts.getValue(effect.error))
                    }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        DetailScreen(
            state = state.toUiState { error -> errorTexts.getValue(error) },
            dispatch = viewModel::dispatch,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}
