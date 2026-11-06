@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.queues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.rusuh.app.ui.components.QueueListItem
import com.rusuh.app.ui.components.QueueListItemUi
import com.rusuh.app.ui.components.RusuhBottomBar
import com.rusuh.app.ui.components.RusuhTab
import com.rusuh.app.ui.theme.RusuhSpacing

/**
 * UI-level render state for the My Queues (Antrian) screen.
 *
 * NOT an MVI state — a presentation-only discriminator so the stateless screen
 * can render each visual placeholder the design calls for (loading / content /
 * empty / error). FR-APP-01-M3 introduces the real MyQueuesViewModel and maps
 * its MVI state onto these cases.
 */
sealed interface MyQueuesUiState {
    /** Fetching the queues the user is in. */
    data object Loading : MyQueuesUiState

    /** Loaded: one or more active queues to display. */
    data class Content(val queues: List<QueueListItemUi>) : MyQueuesUiState

    /** The user is not in any queue right now. */
    data object Empty : MyQueuesUiState

    /**
     * Failed to load the queues.
     *
     * @param message pre-formatted, user-facing error copy supplied by the caller.
     */
    data class Error(val message: String) : MyQueuesUiState
}

/**
 * My Queues (Antrian) screen — FR-APP-01-M2.
 *
 * A top-level tab screen: a [Scaffold] whose `bottomBar` is the shared
 * [RusuhBottomBar] with [RusuhTab.Antrian] selected, and whose content is a
 * [LazyColumn] of [QueueListItem]s, plus the empty / loading (skeleton) / error
 * (retry) variants.
 *
 * Stateless and UI-only: it renders the [state] it is handed and raises
 * callbacks; there is NO ViewModel, NO repository and NO API here. The default
 * [state] carries placeholder queues so previews and the nav graph render
 * something before M3 wires real data.
 *
 * TODO(M3): wire MyQueuesViewModel — collect the MVI state/effects and map them
 *  onto [MyQueuesUiState], replacing the caller-supplied [state].
 * TODO(M4): feed [QueueListItemUi] from the live my-queues API (FR-07/FR-10).
 *
 * @param state what to render.
 * @param actions the callbacks the screen raises (row tap, tab switch, browse,
 *   retry), bundled so the screen takes one handler object. All default to
 *   no-ops so previews and non-navigating hosts can render it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyQueuesScreen(
    modifier: Modifier = Modifier,
    state: MyQueuesUiState = MyQueuesUiState.Content(placeholderQueues()),
    actions: MyQueuesActions = MyQueuesActions(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Antrian saya",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        },
        bottomBar = {
            RusuhBottomBar(
                selectedTab = RusuhTab.Antrian,
                onTabSelected = actions.onTabSelected,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (state) {
                MyQueuesUiState.Loading -> LoadingState()
                MyQueuesUiState.Empty -> EmptyState(onBrowseEvents = actions.onBrowseEvents)
                is MyQueuesUiState.Error -> ErrorState(message = state.message, onRetry = actions.onRetry)
                is MyQueuesUiState.Content ->
                    QueueList(queues = state.queues, onOpenWaitingRoom = actions.onOpenWaitingRoom)
            }
        }
    }
}

/**
 * The callbacks [MyQueuesScreen] raises, bundled so the screen takes one handler
 * object instead of several loose lambdas (mirrors HomeScreen's HomeActions).
 *
 * @param onOpenWaitingRoom invoked with an event id when a row is tapped; the
 *   nav graph pushes that event's Waiting Room.
 * @param onTabSelected invoked when another bottom-bar tab is tapped; the nav
 *   graph navigates to that top-level destination.
 * @param onBrowseEvents invoked from the empty state's "lihat event" action;
 *   the nav graph returns the user to the Events tab.
 * @param onRetry invoked from the error state's retry action; reloads the queues
 *   once M3 wires the ViewModel.
 */
data class MyQueuesActions(
    val onOpenWaitingRoom: (String) -> Unit = {},
    val onTabSelected: (RusuhTab) -> Unit = {},
    val onBrowseEvents: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

@Composable
private fun QueueList(
    queues: List<QueueListItemUi>,
    onOpenWaitingRoom: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(RusuhSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
    ) {
        items(items = queues, key = { it.eventId }) { queue ->
            QueueListItem(data = queue, onClick = onOpenWaitingRoom)
        }
    }
}

/**
 * Skeleton loading state: a few placeholder rows so the list keeps its shape
 * while the first load runs. A spinner tops the column for an explicit "loading"
 * cue (matches the Waiting Room / Detail loading affordance).
 */
@Composable
private fun LoadingState() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(RusuhSpacing.xl)
                .semantics { contentDescription = "Memuat antrian" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
    ) {
        CircularProgressIndicator()
        repeat(SKELETON_ROW_COUNT) {
            SkeletonRow()
        }
    }
}

@Composable
private fun SkeletonRow() {
    Surface(
        modifier = Modifier.fillMaxWidth().height(RusuhSpacing.queueSkeletonRow),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {}
}

@Composable
private fun EmptyState(onBrowseEvents: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(RusuhSpacing.xl)
                .semantics { contentDescription = "Belum ada antrian. Lihat event untuk mulai mengantre." },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Belum ada antrian",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Anda belum mengantre acara apa pun. Jelajahi event untuk mulai.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onBrowseEvents, modifier = Modifier.padding(top = RusuhSpacing.xl)) {
            Text(text = "Lihat event")
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(RusuhSpacing.xl)
                .semantics { contentDescription = "Gagal memuat antrian. $message" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Gagal memuat antrian",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = RusuhSpacing.xl)) {
            Text(text = "Coba lagi")
        }
    }
}

private const val SKELETON_ROW_COUNT = 3
