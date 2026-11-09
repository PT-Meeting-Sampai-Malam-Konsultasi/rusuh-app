package com.rusuh.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rusuh.app.ui.components.ErrorState
import com.rusuh.app.ui.components.EventCard
import com.rusuh.app.ui.components.LoadingState
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.RusuhTheme
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.app_name
import rusuh_app.shared.generated.resources.home_all_events
import rusuh_app.shared.generated.resources.home_empty_title
import rusuh_app.shared.generated.resources.home_event_count
import rusuh_app.shared.generated.resources.home_pull_to_refresh

/** Minimum width a single event-card column occupies; drives the responsive grid. */
private val EVENT_CARD_MIN_WIDTH = 320.dp

/** Minimum height so the empty-state column fills the viewport and stays pullable. */
private val EMPTY_STATE_MIN_HEIGHT = 400.dp

private val APP_BAR_HEIGHT = 60.dp
private val LOGO_SIZE = 28.sp
private val HEADING_SIZE = 28.sp

/**
 * The Home screen: an app bar plus a responsive, pull-to-refresh event list that
 * renders M1's [EventCard] for each event in [HomeViewModel]'s state.
 *
 * This is the UI half of FR-01: it collects [HomeState] from the [HomeViewModel]
 * and dispatches [HomeIntent]s; the data still comes from M3's stub repository
 * until M4 wires the real API. Navigation is pushed out to [onEventClick] so the
 * shared screen stays free of any navigator dependency.
 *
 * @param viewModel the [HomeViewModel] providing state, effects and intent sink.
 * @param onEventClick invoked with an event id when the user opens an event.
 *   Defaults to a no-op so previews and non-navigating hosts can render the screen;
 *   a host that wants navigation must pass a real callback (the nav graph wires it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onEventClick: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Re-run the initial load if the view model instance itself changes, not only
    // on first composition, so a swapped-in view model still loads its events.
    LaunchedEffect(viewModel) {
        viewModel.dispatch(HomeIntent.LoadEvents)
    }

    // Keep the latest onEventClick without restarting the effect collector, so a
    // recomposition that supplies a new callback never fires the stale one.
    val currentOnEventClick by rememberUpdatedState(onEventClick)

    // One-time effects: navigate on tap, surface transient errors as a snackbar.
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToEvent -> currentOnEventClick(effect.id)
                is HomeEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RusuhColors.Paper,
        topBar = { HomeAppBar() },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        HomeContent(
            state = state,
            contentPadding = innerPadding,
            actions =
                HomeActions(
                    onRefresh = { viewModel.dispatch(HomeIntent.RefreshEvents) },
                    onRetry = { viewModel.dispatch(HomeIntent.RefreshEvents) },
                    onEventClick = { id -> viewModel.dispatch(HomeIntent.EventClicked(id)) },
                ),
        )
    }
}

/**
 * The user actions the home content can raise, bundled so the content composable
 * takes one handler object instead of three loose lambdas.
 */
private data class HomeActions(
    val onRefresh: () -> Unit,
    val onRetry: () -> Unit,
    val onEventClick: (String) -> Unit,
)

/** Paper app bar: wordmark with a pink "!" over a 2.5dp ink rule (hi-fi `.appbar`). */
@Composable
private fun HomeAppBar() {
    Column(modifier = Modifier.fillMaxWidth().background(RusuhColors.Paper)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(APP_BAR_HEIGHT).padding(horizontal = RusuhSpacing.xl),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text =
                    buildAnnotatedString {
                        append(stringResource(Res.string.app_name).uppercase())
                        withStyle(SpanStyle(color = RusuhColors.Pink)) { append("!") }
                    },
                style = MaterialTheme.typography.headlineLarge,
                fontSize = LOGO_SIZE,
                fontWeight = FontWeight.Bold,
                color = RusuhColors.Ink,
            )
        }
        HorizontalDivider(thickness = RusuhSpacing.inkBorder, color = RusuhColors.Ink)
    }
}

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
        contentPadding = PaddingValues(RusuhSpacing.xl),
        horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.xl),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ListHeading(count = events.size) }
        itemsIndexed(items = events, key = { _, event -> event.id }) { index, event ->
            EventCard(
                data = event.toEventCardData(),
                onClick = { onEventClick(event.id) },
                posterTilt = if (index % 2 == 0) RusuhSpacing.POSTER_TILT_ODD else RusuhSpacing.POSTER_TILT_EVEN,
            )
        }
    }
}

/** "All events" heading, count line and ink rule above the list (hi-fi `.list-h`). */
@Composable
private fun ListHeading(count: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.home_all_events),
            style = MaterialTheme.typography.headlineLarge,
            fontSize = HEADING_SIZE,
            fontWeight = FontWeight.Bold,
            color = RusuhColors.Ink,
        )
        Text(
            text = stringResource(Res.string.home_event_count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = RusuhColors.Ink2,
        )
        Spacer(Modifier.height(RusuhSpacing.lg))
        HorizontalDivider(thickness = RusuhSpacing.inkBorder, color = RusuhColors.Ink)
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
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.home_pull_to_refresh),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Preview helper wrapping [HomeScreen] in [RusuhTheme].
 *
 * The `@Preview` annotation is intentionally omitted: it lives in
 * `compose.components.uiToolingPreview`, which this module does not depend on.
 */
@Suppress("UnusedPrivateMember")
@Composable
private fun HomeScreenPreview() {
    RusuhTheme {
        HomeScreen(viewModel = HomeViewModel(StubHomeRepository()))
    }
}
