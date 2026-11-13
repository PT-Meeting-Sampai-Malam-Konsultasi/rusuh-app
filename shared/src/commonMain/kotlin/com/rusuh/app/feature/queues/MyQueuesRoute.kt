package com.rusuh.app.feature.queues

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.rusuh.app.ui.components.RusuhTab
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.my_queues_error_body
import rusuh_app.shared.generated.resources.my_queues_refresh_failed
import rusuh_app.shared.generated.resources.resume_expired_notice

/**
 * Stateful host for [MyQueuesScreen]: collects [viewModel] state, forwards screen
 * actions as intents, and turns effects into the navigation callbacks.
 */
@Composable
@Suppress("LongParameterList")
fun MyQueuesRoute(
    viewModel: MyQueuesViewModel,
    onOpenWaitingRoom: (String) -> Unit,
    onContinueBooking: (String) -> Unit,
    onContinuePayment: (String) -> Unit,
    onSwitchTab: (RusuhTab) -> Unit,
    onBrowseEvents: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val openWaitingRoom by rememberUpdatedState(onOpenWaitingRoom)
    val continueBooking by rememberUpdatedState(onContinueBooking)
    val continuePayment by rememberUpdatedState(onContinuePayment)
    val switchTab by rememberUpdatedState(onSwitchTab)
    val browseEvents by rememberUpdatedState(onBrowseEvents)

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) { viewModel.dispatch(MyQueuesIntent.Load) }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is MyQueuesEffect.NavigateToWaitingRoom -> openWaitingRoom(effect.eventId)
                is MyQueuesEffect.ContinueBooking -> continueBooking(effect.eventId)
                is MyQueuesEffect.ContinuePayment -> continuePayment(effect.bookingId)
                is MyQueuesEffect.SwitchTab -> switchTab(effect.tab)
                MyQueuesEffect.GoToEvents -> browseEvents()
                is MyQueuesEffect.ShowError ->
                    snackbarHostState.showSnackbar(getString(effect.message.toStringResource()))
            }
        }
    }

    MyQueuesScreen(
        snackbarHostState = snackbarHostState,
        state = state.toUiState(errorMessage = stringResource(Res.string.my_queues_error_body)),
        actions =
            MyQueuesActions(
                onOpenWaitingRoom = { viewModel.dispatch(MyQueuesIntent.OpenQueue(it)) },
                onContinueOrder = { viewModel.dispatch(MyQueuesIntent.ContinueOrder(it)) },
                onTabSelected = { viewModel.dispatch(MyQueuesIntent.SelectTab(it)) },
                onBrowseEvents = { viewModel.dispatch(MyQueuesIntent.BrowseEvents) },
                onRetry = { viewModel.dispatch(MyQueuesIntent.Retry) },
            ),
    )
}

private fun MyQueuesMessage.toStringResource(): StringResource =
    when (this) {
        MyQueuesMessage.RefreshFailed -> Res.string.my_queues_refresh_failed
        MyQueuesMessage.ResumeExpired -> Res.string.resume_expired_notice
    }
