package com.rusuh.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rusuh.app.feature.account.AccountRoute
import com.rusuh.app.feature.account.AccountViewModel
import com.rusuh.app.feature.account.AccountViewModelHolder
import com.rusuh.app.feature.auth.SignInEffect
import com.rusuh.app.feature.auth.SignInEventContext
import com.rusuh.app.feature.auth.SignInScreen
import com.rusuh.app.feature.auth.SignInUiState
import com.rusuh.app.feature.auth.SignInViewModel
import com.rusuh.app.feature.auth.SignInViewModelHolder
import com.rusuh.app.feature.auth.signInActions
import com.rusuh.app.feature.detail.DetailRoute
import com.rusuh.app.feature.detail.DetailViewModel
import com.rusuh.app.feature.detail.DetailViewModelHolder
import com.rusuh.app.feature.eticket.ETicketRoute
import com.rusuh.app.feature.eticket.ETicketViewModel
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
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

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

        // E-ticket: pushed over the Tiket tab, which keeps its bottom bar (Tiket selected); Back returns to the tab.
        composable(
            route = Screen.ETicket.route,
            arguments = listOf(navArgument(Screen.ETicket.ARG_ORDER_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString(Screen.ETicket.ARG_ORDER_ID).orEmpty()
            val koin = getKoin()
            val viewModel = remember(orderId) { koin.get<ETicketViewModel> { parametersOf(orderId) } }
            ETicketRoute(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onTabSelected = { tab -> navController.switchTab(tab) },
            )
        }

        composable(
            route = Screen.SignIn.route,
            arguments =
                listOf(
                    navArgument(Screen.SignIn.ARG_EVENT_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { backStackEntry ->
            // TODO(AUTH-00 #103): resolve the event from the ARG_EVENT_ID argument and add the
            //  waiting-room entry point (navigate(Screen.SignIn.routeFor(eventId))); until then
            //  nothing pushes this route and the banner shows a placeholder event.
            val koin = getKoin()
            val viewModel =
                viewModel<SignInViewModelHolder>(
                    viewModelStoreOwner = backStackEntry,
                    key = "sign-in",
                    factory = viewModelFactory { initializer { SignInViewModelHolder(koin.get<SignInViewModel>()) } },
                ).viewModel
            val state by viewModel.state.collectAsState()
            val lastState = remember { LastState() }
            lastState.value = state.step
            // TODO(AUTH-00 #103): continue to the queue / Detail once signed in (today: close the screen).
            LaunchedEffect(viewModel) {
                viewModel.effect.collect { effect ->
                    when (effect) {
                        is SignInEffect.SignedIn -> navController.popIfCurrent(Screen.SignIn.route)
                    }
                }
            }
            SignInScreen(
                state = lastState.value,
                event = PLACEHOLDER_SIGN_IN_EVENT,
                status = state.status,
                actions =
                    signInActions(
                        viewModel = viewModel,
                        onDismiss = { navController.popIfCurrent(Screen.SignIn.route) },
                    ),
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
            // TODO(AUTH-00 #103): open Screen.SignIn.routeFor(eventId) when the turn comes and the user is a guest.
            WaitingRoomRoute(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onBrowseEvents = { navController.switchTab(RusuhTab.Event) },
                // The turn: go to Detail and drop the Waiting Room so Back does not return into it.
                onChooseTickets = { id ->
                    navController.navigate(Screen.Detail.routeFor(id)) {
                        popUpTo(Screen.WaitingRoom.route) { inclusive = true }
                    }
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
            // bottom-bar Scaffold wraps it. HomeViewModel decides which taps open the Waiting Room,
            // which joins the queue.
            HomeScreen(
                viewModel = viewModel,
                onOpenQueue = { eventId -> navController.navigate(Screen.WaitingRoom.routeFor(eventId)) },
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
                    is TicketsEffect.OpenETicket -> navController.navigate(Screen.ETicket.routeFor(effect.orderId))
                    // The Account tab hosts the compact login (decision 2026-11-12).
                    // shortcut: after signing in the user stays on the Account page and taps Tiket again.
                    TicketsEffect.OpenSignIn -> navController.switchTab(RusuhTab.Akun)
                }
            }
        }
        TicketsScreen(
            state = state.ui,
            actions =
                TicketsActions(
                    onViewETicket = { viewModel.dispatch(TicketsIntent.ViewETicket(it)) },
                    onTabSelected = onTabSelected,
                    onBrowseEvents = { viewModel.dispatch(TicketsIntent.BrowseEvents) },
                    onSignIn = { viewModel.dispatch(TicketsIntent.SignIn) },
                    onRetry = { viewModel.dispatch(TicketsIntent.Retry) },
                ),
        )
    }

    composable(Screen.Account.route) { backStackEntry -> AccountDestination(backStackEntry, onTabSelected) }
}

/** The Akun tab: both ViewModels live in the back-stack entry (kept while the tab is saved), see the holders. */
@Composable
private fun AccountDestination(
    backStackEntry: NavBackStackEntry,
    onTabSelected: (RusuhTab) -> Unit,
) {
    val koin = getKoin()
    val accountViewModel =
        viewModel<AccountViewModelHolder>(
            viewModelStoreOwner = backStackEntry,
            key = "account",
            factory = viewModelFactory { initializer { AccountViewModelHolder(koin.get<AccountViewModel>()) } },
        ).viewModel
    val signInViewModel =
        viewModel<SignInViewModelHolder>(
            viewModelStoreOwner = backStackEntry,
            key = "account-sign-in",
            factory = viewModelFactory { initializer { SignInViewModelHolder(koin.get<SignInViewModel>()) } },
        ).viewModel
    AccountRoute(
        accountViewModel = accountViewModel,
        signInViewModel = signInViewModel,
        onTabSelected = onTabSelected,
    )
}

/** Holder for the last sign-in step while the route exits. */
private class LastState(
    var value: SignInUiState = SignInUiState.Email(),
)

/**
 * Pops only while [route] is still the current destination, so a double tap (or a tap during the
 * exit animation) cannot pop the screen below as well.
 */
private fun NavHostController.popIfCurrent(route: String) {
    if (currentDestination?.route == route) popBackStack()
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
 * that wrap an existing screen (Home).
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

@Composable
private fun SplashPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text("Splash Screen")
    }
}

/** Demo event for the banner of the full login until #103 resolves the real one (synthetic data). */
private val PLACEHOLDER_SIGN_IN_EVENT =
    SignInEventContext(name = "Samudra", tag = "Tur Gelombang 2026", openingTime = "10.00 WIB")
