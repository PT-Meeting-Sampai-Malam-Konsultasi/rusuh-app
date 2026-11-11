package com.rusuh.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rusuh.app.ui.components.ErrorState
import com.rusuh.app.ui.components.EventCard
import com.rusuh.app.ui.components.LoadingState
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.RusuhTheme
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.home_empty_title
import rusuh_app.shared.generated.resources.home_pull_to_refresh
import rusuh_app.shared.generated.resources.home_section_all_events
import rusuh_app.shared.generated.resources.home_section_all_events_count

/** Minimum width a single event-card column occupies; drives the responsive grid. */
private val EVENT_CARD_MIN_WIDTH = 320.dp

/** Minimum height so the empty-state column fills the viewport and stays pullable. */
private val EMPTY_STATE_MIN_HEIGHT = 400.dp

/**
 * The Home screen (stateful host): collects [HomeState] from the [HomeViewModel],
 * runs the initial load and one-time effects, and delegates rendering to the pure
 * [HomeScreen] overload that takes `(state, dispatch)`.
 *
 * Data still comes from M3's stub repository until M4 wires the real API.
 * Navigation is pushed out to [onEventClick] so the shared screen stays free of
 * any navigator dependency.
 *
 * @param viewModel the [HomeViewModel] providing state, effects and intent sink.
 * @param onOpenQueue invoked with an event id when the user taps an event whose queue can be joined (the
 *   ViewModel decides, see [canQueue]); the Waiting Room does the join.
 *   Defaults to a no-op so previews and non-navigating hosts can render the screen;
 *   a host that wants navigation must pass a real callback (the nav graph wires it).
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onOpenQueue: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Re-run the initial load if the view model instance itself changes, not only
    // on first composition, so a swapped-in view model still loads its events.
    LaunchedEffect(viewModel) {
        viewModel.dispatch(HomeIntent.LoadEvents)
    }

    // Keep the latest onOpenQueue without restarting the effect collector, so a
    // recomposition that supplies a new callback never fires the stale one.
    val currentOnOpenQueue by rememberUpdatedState(onOpenQueue)

    // One-time effects: navigate on tap, surface transient errors as a snackbar.
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToQueue -> currentOnOpenQueue(effect.id)
                is HomeEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    HomeScreen(
        state = state,
        dispatch = viewModel::dispatch,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Pure Home screen: renders [state] and raises [HomeIntent]s through [dispatch].
 * Holds no view model, so it previews and tests with plain data.
 *
 * Layout follows the Figma mobile frame (Home · Event list · Mobile 390): a paper
 * app bar (RUSUH! logo + ID|EN toggle), an "All events" section header and the
 * event list; the shared bottom bar is hosted by the navigation layer.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    dispatch: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RusuhColors.Paper,
        contentColor = RusuhColors.Ink,
        topBar = { HomeAppBar(selectedLanguage = HomeLanguage.ID) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        HomeContent(
            state = state,
            contentPadding = innerPadding,
            actions =
                HomeActions(
                    onRefresh = { dispatch(HomeIntent.RefreshEvents) },
                    onRetry = { dispatch(HomeIntent.RefreshEvents) },
                    onEventClick = { id -> dispatch(HomeIntent.EventClicked(id)) },
                ),
        )
    }
}

/**
 * The user actions the home content can raise, bundled so the content composable
 * takes one handler object instead of three loose lambdas.
 */
@Immutable
private data class HomeActions(
    val onRefresh: () -> Unit,
    val onRetry: () -> Unit,
    val onEventClick: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeState,
    contentPadding: PaddingValues,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        when {
            // First load (no data yet): show skeletons, not the pull indicator.
            state.isLoading && state.events.isEmpty() ->
                LoadingState(modifier = Modifier.padding(RusuhSpacing.xl))

            state.error != null && state.events.isEmpty() ->
                ErrorState(message = state.error, onRetry = actions.onRetry)

            else ->
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = actions.onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.events.isEmpty()) {
                        EmptyState()
                    } else {
                        EventGrid(events = state.events, onEventClick = actions.onEventClick)
                    }
                }
        }
    }
}

/**
 * Responsive event grid: an adaptive column count ([GridCells.Adaptive]) gives a
 * single column on phones and multiple columns on wide screens from one layout.
 * The "All events" section header spans the full width above the cards.
 */
@Composable
private fun EventGrid(
    events: List<HomeEventItem>,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = EVENT_CARD_MIN_WIDTH),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = RusuhSpacing.xl, vertical = RusuhSpacing.xxl),
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.xl),
    ) {
        item(key = SECTION_HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(eventCount = events.size)
        }
        items(items = events, key = { it.id }) { event ->
            EventCard(
                data = event.toEventCardData(),
                // Cards that cannot be queued for never dispatch (the ViewModel guards again).
                onClick = { if (event.status.canQueue()) onEventClick(event.id) },
            )
        }
    }
}

private const val SECTION_HEADER_KEY = "home_section_header"

/** "Semua event" title with the event count underneath and a heavy ink rule. */
@Composable
private fun SectionHeader(
    eventCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = RusuhColors.Ink,
                        start = Offset(0f, size.height - RusuhSpacing.ruleThick.toPx() / 2),
                        end = Offset(size.width, size.height - RusuhSpacing.ruleThick.toPx() / 2),
                        strokeWidth = RusuhSpacing.ruleThick.toPx(),
                    )
                }.padding(bottom = RusuhSpacing.lg),
    ) {
        Text(
            text = stringResource(Res.string.home_section_all_events),
            style = MaterialTheme.typography.headlineSmall,
            color = RusuhColors.Ink,
        )
        Text(
            text = stringResource(Res.string.home_section_all_events_count, eventCount),
            style = MaterialTheme.typography.bodyMedium,
            color = RusuhColors.Ink2,
        )
    }
}

/**
 * Shown inside the pull-to-refresh box when the loaded list is empty.
 *
 * Uses a vertically-scrollable column so [PullToRefreshBox] has a scroll
 * connection and the pull-to-refresh gesture still works with no items.
 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = EMPTY_STATE_MIN_HEIGHT)
                .padding(RusuhSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.home_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            color = RusuhColors.Ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.home_pull_to_refresh),
            style = MaterialTheme.typography.bodyMedium,
            color = RusuhColors.Ink2,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Preview helpers wrapping the pure [HomeScreen] in [RusuhTheme], one per state.
 *
 * The `@Preview` annotation is intentionally omitted: it lives in
 * `compose.components.uiToolingPreview`, which this module does not depend on.
 */
@Suppress("UnusedPrivateMember")
@Composable
private fun HomeScreenPreview() {
    RusuhTheme {
        HomeScreen(state = HomeState(events = homePreviewEvents()), dispatch = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
private fun HomeScreenLoadingPreview() {
    RusuhTheme { HomeScreen(state = HomeState(isLoading = true), dispatch = {}) }
}

@Suppress("UnusedPrivateMember")
@Composable
private fun HomeScreenEmptyPreview() {
    RusuhTheme { HomeScreen(state = HomeState(), dispatch = {}) }
}

@Suppress("UnusedPrivateMember")
@Composable
private fun HomeScreenErrorPreview() {
    RusuhTheme { HomeScreen(state = HomeState(error = "Failed to load events"), dispatch = {}) }
}
