@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.queues

import androidx.compose.runtime.Composable
import com.rusuh.app.ui.theme.RusuhTheme

// Preview-style sample composables (no @Preview annotation: commonMain has no
// uiTooling-preview dependency, same convention as QueuePositionCardPreviews).
// Placeholder data only.

@Composable
internal fun MyQueuesContentPreview() {
    RusuhTheme { MyQueuesScreen(state = MyQueuesUiState.Content(placeholderQueues())) }
}

@Composable
internal fun MyQueuesEmptyPreview() {
    RusuhTheme { MyQueuesScreen(state = MyQueuesUiState.Empty) }
}

@Composable
internal fun MyQueuesLoadingPreview() {
    RusuhTheme { MyQueuesScreen(state = MyQueuesUiState.Loading) }
}

@Composable
internal fun MyQueuesErrorPreview() {
    RusuhTheme { MyQueuesScreen(state = MyQueuesUiState.Error("Periksa koneksi Anda lalu coba lagi.")) }
}
