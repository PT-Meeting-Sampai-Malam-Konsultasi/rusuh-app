@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.queues

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.rusuh.app.ui.components.QueueListItem
import com.rusuh.app.ui.components.QueueListItemUi
import com.rusuh.app.ui.components.RusuhBottomBar
import com.rusuh.app.ui.components.RusuhTab
import com.rusuh.app.ui.theme.RusuhBrandColors
import com.rusuh.app.ui.theme.RusuhBrandMetrics
import com.rusuh.app.ui.theme.RusuhShapes
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.action_retry
import rusuh_app.shared.generated.resources.my_queues_count
import rusuh_app.shared.generated.resources.my_queues_empty_action
import rusuh_app.shared.generated.resources.my_queues_empty_body
import rusuh_app.shared.generated.resources.my_queues_empty_title
import rusuh_app.shared.generated.resources.my_queues_error_title
import rusuh_app.shared.generated.resources.my_queues_loading_description
import rusuh_app.shared.generated.resources.my_queues_title

/**
 * UI-level render state for the My Queues (Antrian) screen.
 *
 * NOT an MVI state: a presentation-only discriminator so the stateless screen
 * can render each visual placeholder the design calls for (loading / content /
 * empty / error). FR-APP-01-M3 introduces the real MyQueuesViewModel and maps
 * its MVI state onto these cases.
 */
@Immutable
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

/** Language codes offered by the header toggle (hi-fi ID | EN). */
internal enum class AppLanguageOption(val code: String) {
    Indonesian("id"),
    English("en"),
}

/**
 * My Queues (Antrian) screen, FR-APP-01-M2.
 *
 * A top-level tab screen: a [Scaffold] on the paper background with the RUSUH!
 * logo + ID|EN toggle header, the shared [RusuhBottomBar] (Antrian selected),
 * and a [LazyColumn] of [QueueListItem]s, plus the empty / loading (skeleton) /
 * error (retry) variants. Layout follows the Waiting Room and Home frames of the
 * Figma file (no dedicated My Queues frame exists).
 *
 * Stateless: it renders the [state] it is handed and raises [actions]; there is
 * NO ViewModel, NO repository and NO API here. The header's ID|EN toggle is
 * visual only until locale switching exists.
 *
 * TODO(M3): wire MyQueuesViewModel, collect the MVI state/effects and map them
 *  onto [MyQueuesUiState], replacing the caller-supplied [state].
 * TODO(M4): feed [QueueListItemUi] from the live my-queues API (FR-07/FR-10).
 *
 * @param state what to render.
 * @param actions the callbacks the screen raises (row tap, tab switch, browse,
 *   retry), bundled so the screen takes one handler object. All
 *   default to no-ops so previews and non-navigating hosts can render it.
 */
@Composable
fun MyQueuesScreen(
    modifier: Modifier = Modifier,
    state: MyQueuesUiState = MyQueuesUiState.Content(placeholderQueues()),
    actions: MyQueuesActions = MyQueuesActions(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RusuhBrandColors.Paper,
        // The header pads the status bar itself and the bottom bar pads the
        // navigation bar itself, so the Scaffold's own insets are never added on top.
        topBar = { MyQueuesHeader(language = AppLanguageOption.Indonesian) },
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
 * @param onBrowseEvents invoked from the empty state's action; the nav graph
 *   returns the user to the Events tab.
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
        contentPadding = PaddingValues(horizontal = RusuhSpacing.xl, vertical = RusuhSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
    ) {
        item(key = "heading") { PageHeading(queueCount = queues.size) }
        items(items = queues, key = { it.eventId }) { queue ->
            QueueListItem(data = queue, onClick = onOpenWaitingRoom)
        }
    }
}

/** Page title (display face) with the active-queue count beneath. */
@Composable
private fun PageHeading(queueCount: Int?) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = RusuhSpacing.md)) {
        Text(
            text = stringResource(Res.string.my_queues_title),
            style = MaterialTheme.typography.headlineLarge,
            color = RusuhBrandColors.Ink,
            modifier = Modifier.semantics { heading() },
        )
        if (queueCount != null) {
            Text(
                text = stringResource(Res.string.my_queues_count, queueCount),
                style = MaterialTheme.typography.bodyMedium,
                color = RusuhBrandColors.Ink3,
            )
        }
    }
}

/**
 * Skeleton loading state: placeholder cards that keep the list's shape while the
 * first load runs. Announced as a single "loading" element.
 */
@Composable
private fun LoadingState() {
    val description = stringResource(Res.string.my_queues_loading_description)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(RusuhSpacing.xl)
                .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.xxl),
    ) {
        PageHeading(queueCount = null)
        repeat(SKELETON_ROW_COUNT) { SkeletonRow() }
    }
}

@Composable
private fun SkeletonRow() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(RusuhSpacing.queueSkeletonRow)
                .background(RusuhBrandColors.PaperDeep, RusuhShapes.small)
                .border(BorderStroke(RusuhBrandMetrics.borderWidth, RusuhBrandColors.Hair), RusuhShapes.small),
    )
}

@Composable
private fun EmptyState(onBrowseEvents: () -> Unit) {
    CenteredMessage(
        title = stringResource(Res.string.my_queues_empty_title),
        body = stringResource(Res.string.my_queues_empty_body),
        titleColor = RusuhBrandColors.Ink,
        actionLabel = stringResource(Res.string.my_queues_empty_action),
        onAction = onBrowseEvents,
    )
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    CenteredMessage(
        title = stringResource(Res.string.my_queues_error_title),
        body = message,
        titleColor = MaterialTheme.colorScheme.error,
        actionLabel = stringResource(Res.string.action_retry),
        onAction = onRetry,
    )
}

/** Shared empty/error layout: ink-outlined paper card with a title, body and one action. */
@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    titleColor: Color,
    actionLabel: String,
    onAction: () -> Unit,
) {
    // Scrollable so the card stays reachable at large font scales.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(RusuhSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(RusuhBrandColors.Card, RusuhShapes.small)
                    .border(RusuhBrandMetrics.borderWidth, RusuhBrandColors.Ink, RusuhShapes.small)
                    .padding(RusuhSpacing.xxl)
                    .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = titleColor,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = RusuhBrandColors.Ink2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(RusuhSpacing.md))
            Button(
                onClick = onAction,
                shape = RusuhShapes.small,
                border = BorderStroke(RusuhBrandMetrics.borderWidth, RusuhBrandColors.Ink),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = RusuhBrandColors.Pink,
                        contentColor = Color.White,
                    ),
            ) {
                Text(text = actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private const val SKELETON_ROW_COUNT = 3
