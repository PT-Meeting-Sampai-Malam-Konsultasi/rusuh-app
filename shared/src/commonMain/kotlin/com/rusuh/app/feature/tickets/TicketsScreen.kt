package com.rusuh.app.feature.tickets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rusuh.app.feature.queues.AppLanguageOption
import com.rusuh.app.feature.queues.MyQueuesHeader
import com.rusuh.app.ui.components.RusuhBottomBar
import com.rusuh.app.ui.components.RusuhTab
import com.rusuh.app.ui.components.TicketOrderCard
import com.rusuh.app.ui.components.TicketOrderUi
import com.rusuh.app.ui.theme.RusuhBrandColors
import com.rusuh.app.ui.theme.RusuhBrandMetrics
import com.rusuh.app.ui.theme.RusuhShapes
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.tickets_empty_action
import rusuh_app.shared.generated.resources.tickets_empty_body
import rusuh_app.shared.generated.resources.tickets_empty_title
import rusuh_app.shared.generated.resources.tickets_loading_description
import rusuh_app.shared.generated.resources.tickets_past_count
import rusuh_app.shared.generated.resources.tickets_past_title
import rusuh_app.shared.generated.resources.tickets_signed_out_action
import rusuh_app.shared.generated.resources.tickets_signed_out_body
import rusuh_app.shared.generated.resources.tickets_signed_out_title
import rusuh_app.shared.generated.resources.tickets_subtitle
import rusuh_app.shared.generated.resources.tickets_title
import rusuh_app.shared.generated.resources.tickets_upcoming_count
import rusuh_app.shared.generated.resources.tickets_upcoming_title

/**
 * UI-level render state for the Tiket tab (Figma "Event Purchased": orders,
 * no tickets yet, signed out). Presentation-only discriminator, NOT an MVI
 * state; TICKET-00-M3 maps the real ViewModel state onto it.
 */
@Immutable
sealed interface TicketsUiState {
    data object Loading : TicketsUiState

    /** Signed in with orders; either list may be empty but not both. */
    data class Content(
        val upcoming: List<TicketOrderUi>,
        val past: List<TicketOrderUi>,
    ) : TicketsUiState

    /** Signed in, nothing paid yet. */
    data object Empty : TicketsUiState

    /** Tickets belong to the account, so a guest must sign in first. */
    data object SignedOut : TicketsUiState
}

/**
 * Callbacks the Tiket tab raises, bundled like HomeActions / MyQueuesActions.
 * All default to no-ops so previews can render the screen.
 */
data class TicketsActions(
    val onViewETicket: (String) -> Unit = {},
    val onTabSelected: (RusuhTab) -> Unit = {},
    val onBrowseEvents: () -> Unit = {},
    val onSignIn: () -> Unit = {},
)

/**
 * Tiket tab (TICKET-00-M2): purchased orders grouped Akan datang / Sudah lewat,
 * plus the empty, signed-out and loading variants, inside the shared header and
 * bottom bar (Tiket selected). Stateless and placeholder-driven.
 *
 * TODO(M3): wire the TicketsViewModel and map its state onto [TicketsUiState].
 * TODO(M4): feed orders from the my-tickets API.
 */
@Composable
fun TicketsScreen(
    modifier: Modifier = Modifier,
    state: TicketsUiState = TicketsUiState.Content(placeholderUpcomingOrders(), placeholderPastOrders()),
    actions: TicketsActions = TicketsActions(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RusuhBrandColors.Paper,
        topBar = { MyQueuesHeader(language = AppLanguageOption.Indonesian) },
        bottomBar = { RusuhBottomBar(selectedTab = RusuhTab.Tiket, onTabSelected = actions.onTabSelected) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (state) {
                TicketsUiState.Loading -> LoadingState()
                TicketsUiState.Empty ->
                    MessageState(
                        title = stringResource(Res.string.tickets_empty_title),
                        body = stringResource(Res.string.tickets_empty_body),
                        actionLabel = stringResource(Res.string.tickets_empty_action),
                        onAction = actions.onBrowseEvents,
                    )
                TicketsUiState.SignedOut ->
                    MessageState(
                        title = stringResource(Res.string.tickets_signed_out_title),
                        body = stringResource(Res.string.tickets_signed_out_body),
                        actionLabel = stringResource(Res.string.tickets_signed_out_action),
                        onAction = actions.onSignIn,
                    )
                is TicketsUiState.Content -> OrderList(state, actions.onViewETicket)
            }
        }
    }
}

@Composable
private fun OrderList(
    state: TicketsUiState.Content,
    onViewETicket: (String) -> Unit,
) {
    val ticketCount = state.upcoming.sumOf { order -> order.attendees.size }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = RusuhSpacing.xl,
                vertical = RusuhSpacing.xl,
            ),
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
    ) {
        item(key = "heading") { PageHeading(showSubtitle = true) }
        if (state.upcoming.isNotEmpty()) {
            item(key = "upcoming-header") {
                SectionHeader(
                    title = stringResource(Res.string.tickets_upcoming_title),
                    count = stringResource(Res.string.tickets_upcoming_count, state.upcoming.size, ticketCount),
                )
            }
            items(items = state.upcoming, key = { it.orderId }) { TicketOrderCard(it, onViewETicket) }
        }
        if (state.past.isNotEmpty()) {
            item(key = "past-header") {
                SectionHeader(
                    title = stringResource(Res.string.tickets_past_title),
                    count = stringResource(Res.string.tickets_past_count, state.past.size),
                )
            }
            items(items = state.past, key = { it.orderId }) { TicketOrderCard(it, onViewETicket) }
        }
    }
}

@Composable
private fun PageHeading(showSubtitle: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = RusuhSpacing.md)) {
        Text(
            text = stringResource(Res.string.tickets_title),
            style = MaterialTheme.typography.headlineLarge,
            color = RusuhBrandColors.Ink,
            modifier = Modifier.semantics { heading() },
        )
        if (showSubtitle) {
            Text(
                text = stringResource(Res.string.tickets_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = RusuhBrandColors.Ink2,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = RusuhSpacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = RusuhSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = RusuhBrandColors.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = count, style = MaterialTheme.typography.bodySmall, color = RusuhBrandColors.Ink3)
        }
        Box(Modifier.fillMaxWidth().height(RusuhBrandMetrics.borderWidth).background(RusuhBrandColors.Ink))
    }
}

@Composable
private fun LoadingState() {
    val description = stringResource(Res.string.tickets_loading_description)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(RusuhSpacing.xl)
                .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.xxl),
    ) {
        PageHeading(showSubtitle = false)
        repeat(SKELETON_CARD_COUNT) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(SKELETON_CARD_HEIGHT)
                        .background(RusuhBrandColors.PaperDeep, RusuhShapes.small),
            )
        }
    }
}

/** Empty / signed-out layout (Figma): left-aligned title, body and one ink action. */
@Composable
private fun MessageState(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(RusuhSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.md),
    ) {
        PageHeading(showSubtitle = false)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = RusuhBrandColors.Ink,
        )
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = RusuhBrandColors.Ink2)
        Button(
            onClick = onAction,
            shape = RusuhShapes.small,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = RusuhBrandColors.Ink,
                    contentColor = RusuhBrandColors.Paper,
                ),
        ) { Text(text = "$actionLabel →", fontWeight = FontWeight.SemiBold) }
    }
}

private const val SKELETON_CARD_COUNT = 2
private val SKELETON_CARD_HEIGHT = 220.dp
