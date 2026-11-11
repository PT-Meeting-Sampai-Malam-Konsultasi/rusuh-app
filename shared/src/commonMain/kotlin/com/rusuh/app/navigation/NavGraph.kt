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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rusuh.app.feature.auth.SignInSheet
import com.rusuh.app.feature.auth.rememberPlaceholderSignIn
import com.rusuh.app.feature.detail.DetailRoute
import com.rusuh.app.feature.detail.DetailViewModel
import com.rusuh.app.feature.detail.DetailViewModelHolder
import com.rusuh.app.feature.home.HomeScreen
import com.rusuh.app.feature.home.HomeViewModel
import com.rusuh.app.feature.queues.MyQueuesRoute
import com.rusuh.app.feature.queues.MyQueuesViewModel
import com.rusuh.app.feature.tickets.TicketsActions
import com.rusuh.app.feature.tickets.TicketsEffect
import com.rusuh.app.feature.tickets.TicketsIntent
import com.rusuh.app.feature.tickets.TicketsScreen
import com.rusuh.app.feature.tickets.TicketsViewModel
import com.rusuh.app.feature.waitingroom.WaitingRoomRoute
import com.rusuh.app.feature.waitingroom.WaitingRoomViewModel
import com.rusuh.app.ui.components.RusuhBottomBar
import com.rusuh.app.ui.components.RusuhTab
import com.rusuh.app.ui.theme.RusuhColors
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
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
            route = Screen.Detail.route,
            arguments = listOf(navArgument(Screen.Detail.ARG_EVENT_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString(Screen.Detail.ARG_EVENT_ID).orEmpty()
            val koin = getKoin()
            val viewModel =
                viewModel<DetailViewModelHolder>(
                    viewModelStoreOwner = backStackEntry,
                    key = "detail-$eventId",
                    factory =
                        viewModelFactory {
                            initializer { DetailViewModelHolder(koin.get<DetailViewModel> { parametersOf(eventId) }) }
                        },
                ).viewModel
            DetailRoute(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onHome = { navController.popBackStack(Screen.Home.route, inclusive = false) },
                // TODO(FR-04-M5 #78): temporary queue entry until the join/route decision lands.
                onQueue = { id ->
                    navController.navigate(Screen.WaitingRoom.routeFor(id)) {
                        popUpTo(Screen.Detail.route) { inclusive = true }
                    }
                },
                // TODO(FR-16-M1 #81): no payment route yet; do not navigate.
                onPayment = {},
            )
        }

        composable(
            route = Screen.WaitingRoom.route,
            arguments = listOf(navArgument(Screen.WaitingRoom.ARG_EVENT_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString(Screen.WaitingRoom.ARG_EVENT_ID).orEmpty()
            // remember(eventId): koinInject's parameters lambda is a new instance every recomposition,
            // so resolve once per event id to keep a single ViewModel for this destination.
            val koin = getKoin()
            val viewModel = remember(eventId) { koin.get<WaitingRoomViewModel> { parametersOf(eventId) } }
            WaitingRoomRoute(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onBrowseEvents = { navController.switchTab(RusuhTab.Event) },
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
            // bottom-bar Scaffold wraps it. Tapping an event opens Detail directly.
            // TODO(FR-04-M5 #78): temporary until the queue join/route decision lands.
            HomeScreen(
                viewModel = viewModel,
                onEventClick = { eventId -> navController.navigate(Screen.Detail.routeFor(eventId)) },
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            )
        }
    }

    composable(Screen.Queues.route) {
        MyQueuesRoute(
            viewModel = koinInject<MyQueuesViewModel>(),
            onOpenWaitingRoom = { eventId -> navController.navigate(Screen.WaitingRoom.routeFor(eventId)) },
            onSwitchTab = onTabSelected,
            onBrowseEvents = { navController.switchTab(RusuhTab.Event) },
        )
    }

    composable(Screen.Tickets.route) {
        val viewModel = koinInject<TicketsViewModel>()
        val state by viewModel.state.collectAsState()

        LaunchedEffect(viewModel) { viewModel.dispatch(TicketsIntent.Load) }
        LaunchedEffect(viewModel) {
            viewModel.effect.collect { effect ->
                when (effect) {
                    TicketsEffect.GoToEvents -> navController.switchTab(RusuhTab.Event)
                    // TODO(FR-22-M2 #71): navigate to the e-ticket page once the destination exists.
                    is TicketsEffect.OpenETicket -> Unit
                    // TODO(AUTH-00 #74): navigate to sign-in once the auth flow exists.
                    TicketsEffect.OpenSignIn -> Unit
                }
            }
        }
        // Placeholder sign-in (local UI state only) until AUTH-00-M3 wires the real auth state.
        val signIn = rememberPlaceholderSignIn()
        Box(Modifier.fillMaxSize()) {
            TicketsScreen(
                state = state.ui,
                // Inert for TalkBack while the sheet is open.
                modifier = if (signIn.state != null) Modifier.clearAndSetSemantics {} else Modifier,
                actions =
                    TicketsActions(
                        onViewETicket = { viewModel.dispatch(TicketsIntent.ViewETicket(it)) },
                        onTabSelected = onTabSelected,
                        onBrowseEvents = { viewModel.dispatch(TicketsIntent.BrowseEvents) },
                        onSignIn = signIn::open,
                        onRetry = { viewModel.dispatch(TicketsIntent.Retry) },
                    ),
            )
            signIn.state?.let { SignInSheet(state = it, actions = signIn.actions) }
        }
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
