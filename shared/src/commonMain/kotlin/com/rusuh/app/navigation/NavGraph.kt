package com.rusuh.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rusuh.app.feature.home.HomeScreen
import com.rusuh.app.feature.home.HomeViewModel
import com.rusuh.app.feature.queues.MyQueuesActions
import com.rusuh.app.feature.queues.MyQueuesScreen
import com.rusuh.app.feature.tickets.GetMyTicketsUseCase
import com.rusuh.app.feature.tickets.TicketsActions
import com.rusuh.app.feature.tickets.TicketsScreen
import com.rusuh.app.feature.tickets.TicketsUiState
import com.rusuh.app.feature.tickets.toUiState
import com.rusuh.app.feature.waitingroom.WaitingRoomAction
import com.rusuh.app.feature.waitingroom.WaitingRoomEventUi
import com.rusuh.app.feature.waitingroom.WaitingRoomPhase
import com.rusuh.app.feature.waitingroom.WaitingRoomScreen
import com.rusuh.app.feature.waitingroom.WaitingRoomUiState
import com.rusuh.app.ui.components.QueuePositionUi
import com.rusuh.app.ui.components.RusuhBottomBar
import com.rusuh.app.ui.components.RusuhTab
import com.rusuh.app.ui.theme.RusuhColors
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.tab_coming_soon

/**
 * Main navigation graph for the Rusuh app.
 *
 * Hosts the four top-level tab destinations (Event / Antrian / Tiket / Akun),
 * each of which renders the shared [RusuhBottomBar], plus pushed destinations
 * (the Waiting Room) that do NOT show the bar. Splash is left untouched.
 *
 * Bottom-bar tab selection navigates single-top and pops back to the graph's
 * start destination, so switching tabs does not grow the back stack. State
 * restoration across tabs is a nice-to-have left for a later pass (the tab
 * screens are stateless placeholders today).
 *
 * TODO(M3/M4): swap the placeholder tab screens for their real ViewModels.
 */
@Composable
fun RusuhNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
    ) {
        composable(Screen.Splash.route) { SplashPlaceholder() }

        topLevelTabs(navController)

        composable(
            route = Screen.WaitingRoom.route,
            arguments = listOf(navArgument(Screen.WaitingRoom.ARG_EVENT_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString(Screen.WaitingRoom.ARG_EVENT_ID)
            WaitingRoomScreen(
                state = placeholderWaitingRoomState(eventTitle = eventId.orEmpty()),
                dispatch = { action ->
                    if (action == WaitingRoomAction.Back) navController.popBackStack()
                },
            )
        }
    }
}

/**
 * Registers the four top-level tab destinations. Each screen hosts the shared
 * [RusuhBottomBar] with its own tab selected (the per-tab-screen Scaffold
 * pattern); tab taps route through [onTabSelected].
 */
private fun NavGraphBuilder.topLevelTabs(navController: NavHostController) {
    val onTabSelected: (RusuhTab) -> Unit = { tab -> navController.switchTab(tab) }

    composable(Screen.Home.route) {
        // Event tab: the existing Home screen, retrofitted with the bottom bar.
        // Its business logic is unchanged — only the Scaffold chrome is added.
        val viewModel = koinInject<HomeViewModel>()
        TabScaffold(
            selectedTab = RusuhTab.Event,
            onTabSelected = onTabSelected,
            containerColor = RusuhColors.Paper,
        ) { innerPadding ->
            // Home's own content and business logic are unchanged; only the
            // bottom-bar Scaffold wraps it. onEventClick (Detail routing) is left
            // at its no-op default — the Detail destination is out of this task's
            // scope, so wiring it here is deferred rather than pointed at the
            // wrong screen.
            HomeScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            )
        }
    }

    composable(Screen.Queues.route) {
        MyQueuesScreen(
            actions =
                MyQueuesActions(
                    onOpenWaitingRoom = { eventId -> navController.navigate(Screen.WaitingRoom.routeFor(eventId)) },
                    onTabSelected = onTabSelected,
                    onBrowseEvents = { navController.switchTab(RusuhTab.Event) },
                ),
        )
    }

    composable(Screen.Tickets.route) {
        val getMyTickets = koinInject<GetMyTicketsUseCase>()
        // shortcut: loaded inline until TICKET-00-M3 adds the ViewModel.
        val state by produceState<TicketsUiState>(TicketsUiState.Loading) {
            value = getMyTickets().toUiState()
        }
        TicketsScreen(
            state = state,
            actions =
                TicketsActions(
                    onTabSelected = onTabSelected,
                    onBrowseEvents = { navController.switchTab(RusuhTab.Event) },
                ),
        )
    }

    composable(Screen.Account.route) {
        StubTabScreen(tab = RusuhTab.Akun, onTabSelected = onTabSelected)
    }
}

/**
 * Navigate to the top-level destination for [tab], single-top and popping back
 * to the start destination so tab switches do not stack. A no-op if already
 * on that route.
 */
private fun NavHostController.switchTab(tab: RusuhTab) {
    val route = tab.destinationRoute()
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * A minimal top-level Scaffold hosting the shared bottom bar for tab screens
 * that are just a title today (Tiket / Akun stubs) or that wrap an existing
 * screen (Home).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabScaffold(
    selectedTab: RusuhTab,
    onTabSelected: (RusuhTab) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = containerColor,
        bottomBar = { RusuhBottomBar(selectedTab = selectedTab, onTabSelected = onTabSelected) },
        content = content,
    )
}

/** Placeholder content for a tab whose real screen is not built yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StubTabScreen(
    tab: RusuhTab,
    onTabSelected: (RusuhTab) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(tab.label),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        },
        bottomBar = { RusuhBottomBar(selectedTab = tab, onTabSelected = onTabSelected) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.tab_coming_soon, stringResource(tab.label)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SplashPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text("Splash Screen")
    }
}

/**
 * Placeholder Waiting Room content for a row tap, until M3 feeds the real queue
 * position. shortcut: fixed "waiting" position; swap for live state in M3/M4.
 */
private fun placeholderWaitingRoomState(eventTitle: String): WaitingRoomUiState =
    WaitingRoomUiState.Active(
        event =
            WaitingRoomEventUi(
                title = eventTitle,
                subtitle = "",
                dateTimeLine = "",
                venueLine = "",
            ),
        phase =
            WaitingRoomPhase.Waiting(
                QueuePositionUi(position = 128, estimatedWaitMinutes = 8, ratePerMinute = 310, updatedSecondsAgo = 2),
            ),
    )
