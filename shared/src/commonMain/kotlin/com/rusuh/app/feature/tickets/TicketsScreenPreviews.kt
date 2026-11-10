@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.tickets

import androidx.compose.runtime.Composable
import com.rusuh.app.ui.theme.RusuhTheme

// Preview-style sample composables (no @Preview: commonMain has no uiTooling-preview).

@Composable
internal fun TicketsContentPreview() {
    RusuhTheme { TicketsScreen(state = TicketsUiState.Content(placeholderUpcomingOrders(), placeholderPastOrders())) }
}

@Composable
internal fun TicketsEmptyPreview() {
    RusuhTheme { TicketsScreen(state = TicketsUiState.Empty) }
}

@Composable
internal fun TicketsSignedOutPreview() {
    RusuhTheme { TicketsScreen(state = TicketsUiState.SignedOut) }
}

@Composable
internal fun TicketsLoadingPreview() {
    RusuhTheme { TicketsScreen(state = TicketsUiState.Loading) }
}
