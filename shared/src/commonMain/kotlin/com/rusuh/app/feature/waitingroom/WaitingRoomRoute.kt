package com.rusuh.app.feature.waitingroom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.waiting_error_body

/**
 * Stateful host for the Waiting Room: starts the [viewModel], renders its state through the
 * stateless [WaitingRoomScreen] and routes one-time effects to the navigation callbacks.
 *
 * Navigation to the event Detail is not wired yet: [WaitingRoomEffect.NavigateToDetail] is ignored.
 * TODO(FR-08-M1 #79): navigate to Detail once its route exists (claim #79).
 */
@Composable
fun WaitingRoomRoute(
    viewModel: WaitingRoomViewModel,
    onBack: () -> Unit,
    onBrowseEvents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnBrowseEvents by rememberUpdatedState(onBrowseEvents)

    LaunchedEffect(viewModel) { viewModel.dispatch(WaitingRoomIntent.Start) }
    DisposableEffect(viewModel) { onDispose { viewModel.clear() } }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                WaitingRoomEffect.NavigateBack -> currentOnBack()
                WaitingRoomEffect.NavigateToEvents -> currentOnBrowseEvents()
                // TODO(FR-08-M1 #79): no Detail route yet; do not navigate.
                is WaitingRoomEffect.NavigateToDetail -> Unit
            }
        }
    }

    val errorMessage = stringResource(Res.string.waiting_error_body)
    WaitingRoomScreen(
        state = state.toScreenState(errorMessage),
        dispatch = { action -> viewModel.dispatch(action.toIntent()) },
        modifier = modifier,
    )
}

/** The state the stateless screen renders: a load failure becomes [WaitingRoomUiState.Error]. */
internal fun WaitingRoomState.toScreenState(errorMessage: String): WaitingRoomUiState =
    if (loadFailed) WaitingRoomUiState.Error(errorMessage) else ui
