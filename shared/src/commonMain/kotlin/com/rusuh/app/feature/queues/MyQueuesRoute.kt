package com.rusuh.app.feature.queues

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.rusuh.app.ui.components.RusuhTab
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.my_queues_error_body

/**
 * Stateful host for [MyQueuesScreen]: collects [viewModel] state, forwards screen
 * actions as intents, and turns effects into the navigation callbacks.
 */
@Composable
fun MyQueuesRoute(
    viewModel: MyQueuesViewModel,
    onOpenWaitingRoom: (String) -> Unit,
    onSwitchTab: (RusuhTab) -> Unit,
    onBrowseEvents: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val openWaitingRoom by rememberUpdatedState(onOpenWaitingRoom)
    val switchTab by rememberUpdatedState(onSwitchTab)
    val browseEvents by rememberUpdatedState(onBrowseEvents)

    LaunchedEffect(viewModel) { viewModel.dispatch(MyQueuesIntent.Load) }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is MyQueuesEffect.NavigateToWaitingRoom -> openWaitingRoom(effect.eventId)
                is MyQueuesEffect.SwitchTab -> switchTab(effect.tab)
                MyQueuesEffect.GoToEvents -> browseEvents()
            }
        }
    }

    MyQueuesScreen(
        state = state.toUiState(errorMessage = stringResource(Res.string.my_queues_error_body)),
        actions =
            MyQueuesActions(
                onOpenWaitingRoom = { viewModel.dispatch(MyQueuesIntent.OpenQueue(it)) },
                onTabSelected = { viewModel.dispatch(MyQueuesIntent.SelectTab(it)) },
                onBrowseEvents = { viewModel.dispatch(MyQueuesIntent.BrowseEvents) },
                onRetry = { viewModel.dispatch(MyQueuesIntent.Retry) },
            ),
    )
}
