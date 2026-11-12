@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.eticket

import androidx.compose.foundation.background
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rusuh.app.feature.queues.AppLanguageOption
import com.rusuh.app.feature.queues.MyQueuesHeader
import com.rusuh.app.ui.components.BrutalButton
import com.rusuh.app.ui.components.RusuhBottomBar
import com.rusuh.app.ui.components.RusuhTab
import com.rusuh.app.ui.theme.RusuhBrandColors
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhShapes
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import com.rusuh.app.ui.theme.display
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.eticket_error_action
import rusuh_app.shared.generated.resources.eticket_error_body
import rusuh_app.shared.generated.resources.eticket_error_title
import rusuh_app.shared.generated.resources.eticket_loading_description
import rusuh_app.shared.generated.resources.eticket_not_found_action
import rusuh_app.shared.generated.resources.eticket_not_found_body
import rusuh_app.shared.generated.resources.eticket_not_found_title
import rusuh_app.shared.generated.resources.eticket_page_title

/**
 * Callbacks the e-ticket page raises. All default to no-ops so previews can render the screen.
 *
 * @param onAddToCalendar null while "Tambah ke kalender" is not wired: the button is shown but does nothing.
 */
data class ETicketActions(
    val onTabSelected: (RusuhTab) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBackToTickets: () -> Unit = {},
    // TODO(FR-22 calendar slice): Android calendar intent / iOS EventKit; inert in the first PR.
    val onAddToCalendar: (() -> Unit)? = null,
)

/**
 * Stateful host: starts the [viewModel], renders its state through the stateless [ETicketScreen] and routes
 * the one-time effects. [onBack] returns to the Tiket tab.
 */
@Composable
fun ETicketRoute(
    viewModel: ETicketViewModel,
    onBack: () -> Unit,
    onTabSelected: (RusuhTab) -> Unit,
    modifier: Modifier = Modifier,
    onAddToCalendar: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(viewModel) { viewModel.dispatch(ETicketIntent.Load) }
    DisposableEffect(viewModel) { onDispose { viewModel.clear() } }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ETicketEffect.NavigateBack -> currentOnBack()
            }
        }
    }
    ETicketScreen(
        state = state.ui,
        modifier = modifier,
        actions =
            ETicketActions(
                onTabSelected = onTabSelected,
                onRetry = { viewModel.dispatch(ETicketIntent.Retry) },
                onBackToTickets = { viewModel.dispatch(ETicketIntent.Back) },
                onAddToCalendar = onAddToCalendar,
            ),
    )
}

/**
 * E-ticket page (FR-22): inside the Tiket tab (header and bottom bar with Tiket selected), one scrolling
 * column with the paid header, one ticket card with its QR per holder, and the "Di pintu masuk" steps.
 * Stateless.
 */
@Composable
fun ETicketScreen(
    state: ETicketUiState,
    modifier: Modifier = Modifier,
    actions: ETicketActions = ETicketActions(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RusuhBrandColors.Paper,
        topBar = {
            MyQueuesHeader(
                language = AppLanguageOption.Indonesian,
                title = stringResource(Res.string.eticket_page_title),
            )
        },
        bottomBar = { RusuhBottomBar(selectedTab = RusuhTab.Tiket, onTabSelected = actions.onTabSelected) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (state) {
                ETicketUiState.Loading -> LoadingState()
                is ETicketUiState.Content -> ETicketContent(state.ui, actions.onAddToCalendar)
                ETicketUiState.Error ->
                    MessageState(
                        title = stringResource(Res.string.eticket_error_title),
                        body = stringResource(Res.string.eticket_error_body),
                        actionLabel = stringResource(Res.string.eticket_error_action),
                        onAction = actions.onRetry,
                    )
                ETicketUiState.NotFound ->
                    MessageState(
                        title = stringResource(Res.string.eticket_not_found_title),
                        body = stringResource(Res.string.eticket_not_found_body),
                        actionLabel = stringResource(Res.string.eticket_not_found_action),
                        onAction = actions.onBackToTickets,
                    )
            }
        }
    }
}

@Composable
private fun ETicketContent(
    ui: ETicketUi,
    onAddToCalendar: (() -> Unit)?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = RusuhSpacing.xl, vertical = RusuhSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.xxl),
    ) {
        item(key = "intro") { ETicketIntro(ui, onAddToCalendar) }
        items(items = ui.tickets, key = { it.ticketId }) { ETicketCard(it) }
        item(key = "at-door") { AtTheDoor() }
    }
}

@Composable
private fun LoadingState() {
    val description = stringResource(Res.string.eticket_loading_description)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(RusuhSpacing.xl)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.xxl),
    ) {
        Box(Modifier.fillMaxWidth().height(SKELETON_HEADER_HEIGHT).background(RusuhBrandColors.PaperDeep))
        repeat(SKELETON_CARD_COUNT) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SKELETON_CARD_HEIGHT)
                    .background(RusuhBrandColors.PaperDeep, RusuhShapes.small),
            )
        }
    }
}

/** Error and not-found layout: left-aligned title, body and one ink action. */
@Composable
private fun MessageState(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(RusuhSpacing.xl)
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
    ) {
        Text(
            text = title,
            style = RusuhBrandTextStyles.sectionTitle.display(),
            color = RusuhBrandColors.Ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = body, style = RusuhBrandTextStyles.bodyLarge.body(), color = RusuhBrandColors.Ink2)
        BrutalButton(text = actionLabel, onClick = onAction)
    }
}

private val SKELETON_HEADER_HEIGHT = 160.dp
private val SKELETON_CARD_HEIGHT = 320.dp
private const val SKELETON_CARD_COUNT = 2
