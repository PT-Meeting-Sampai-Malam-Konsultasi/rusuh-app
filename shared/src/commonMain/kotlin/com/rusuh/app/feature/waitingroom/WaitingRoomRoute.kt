package com.rusuh.app.feature.waitingroom

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import rusuh_app.shared.generated.resources.action_retry
import rusuh_app.shared.generated.resources.waiting_error_body
import rusuh_app.shared.generated.resources.waiting_refresh_error

private const val TICK_MILLIS = 1_000L

/**
 * Stateful host for the Waiting Room: starts the [viewModel], renders its state through the
 * stateless [WaitingRoomScreen], ticks countdowns once a second and routes one-time effects.
 *
 * [WaitingRoomEffect.NavigateToDetail] (the turn's "choose tickets" action) goes to [onChooseTickets].
 *
 * The queue entry gate (AUTH-00-M8 #154) ensures users are signed in before reaching this screen.
 * The only sign-in path from here is [onReSignIn], a defensive fallback when the session is lost
 * mid-queue (401/SignInRequiredException during polling); after re-signing in the user resumes
 * the same place (join is idempotent, the place belongs to the device).
 */
@Suppress("LongParameterList") // Navigation callbacks for a route composable.
@Composable
fun WaitingRoomRoute(
    viewModel: WaitingRoomViewModel,
    onBack: () -> Unit,
    onBrowseEvents: () -> Unit,
    onChooseTickets: (String) -> Unit,
    onReSignIn: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnBrowseEvents by rememberUpdatedState(onBrowseEvents)
    val currentOnChooseTickets by rememberUpdatedState(onChooseTickets)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val refreshMessage = stringResource(Res.string.waiting_refresh_error)
    val retryLabel = stringResource(Res.string.action_retry)

    LaunchedEffect(viewModel) { viewModel.dispatch(WaitingRoomIntent.Start) }
    DisposableEffect(viewModel) { onDispose { viewModel.clear() } }
    // Ticks only while a countdown is on screen; the ViewModel derives seconds from server time.
    LaunchedEffect(viewModel, state.hasCountdown) {
        while (state.hasCountdown) {
            delay(TICK_MILLIS)
            viewModel.dispatch(WaitingRoomIntent.Tick)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                WaitingRoomEffect.NavigateBack -> currentOnBack()
                WaitingRoomEffect.NavigateToEvents -> currentOnBrowseEvents()
                // Defensive re-auth: session lost mid-queue opens sign-in to resume.
                is WaitingRoomEffect.ReSignIn -> onReSignIn(effect.eventId)
                WaitingRoomEffect.ShowRefreshError ->
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(refreshMessage, actionLabel = retryLabel)
                        if (result == SnackbarResult.ActionPerformed) viewModel.dispatch(WaitingRoomIntent.Retry)
                    }
                is WaitingRoomEffect.NavigateToDetail -> currentOnChooseTickets(effect.eventId)
            }
        }
    }

    val errorMessage = stringResource(Res.string.waiting_error_body)
    Box(modifier = modifier.fillMaxSize()) {
        WaitingRoomScreen(
            state = state.toScreenState(errorMessage),
            dispatch = { action -> viewModel.dispatch(action.toIntent()) },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}

/** The state the stateless screen renders: a blank error message is filled with [errorMessage]. */
internal fun WaitingRoomState.toScreenState(errorMessage: String): WaitingRoomUiState =
    if (ui is WaitingRoomUiState.Error && ui.message.isBlank()) ui.copy(message = errorMessage) else ui
