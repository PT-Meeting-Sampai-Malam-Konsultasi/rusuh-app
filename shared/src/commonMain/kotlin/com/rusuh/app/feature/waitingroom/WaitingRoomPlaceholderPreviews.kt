@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.waitingroom

import androidx.compose.runtime.Composable
import com.rusuh.app.ui.theme.RusuhTheme

@Composable
internal fun WaitingRoomLoadingPreview() = PlaceholderPreview(WaitingRoomUiState.Loading)

@Composable
internal fun WaitingRoomEmptyPreview() = PlaceholderPreview(WaitingRoomUiState.Empty)

@Composable
internal fun WaitingRoomErrorPreview() = PlaceholderPreview(WaitingRoomUiState.Error("Koneksi terputus. Coba lagi."))

@Composable
private fun PlaceholderPreview(state: WaitingRoomUiState) {
    RusuhTheme { WaitingRoomScreen(state = state, dispatch = {}) }
}
