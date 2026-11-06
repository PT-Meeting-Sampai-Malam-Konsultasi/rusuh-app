@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.waitingroom

import androidx.compose.runtime.Composable
import com.rusuh.app.ui.components.QueuePhase
import com.rusuh.app.ui.components.QueuePositionUi
import com.rusuh.app.ui.theme.RusuhTheme

// Preview-style sample composables wrapped in RusuhTheme with hardcoded
// placeholder data for every render state. No @Preview annotation (the project
// does not depend on compose uiTooling-preview in commonMain; the ui.components
// package follows the same convention), so these are plain @Composable samples a
// platform preview harness can call. Placeholder values only — no ViewModel, no
// repository, no API.

@Composable
internal fun WaitingRoomWaitingPreview() {
    RusuhTheme {
        WaitingRoomScreen(
            state =
                WaitingRoomUiState.Content(
                    QueuePositionUi(
                        position = 42,
                        peopleAhead = 41,
                        phase = QueuePhase.Waiting,
                        estimatedWait = "~8 menit",
                    ),
                ),
            eventTitle = "Langit Sore — Senja Panjang Live",
        )
    }
}

@Composable
internal fun WaitingRoomYourTurnPreview() {
    RusuhTheme {
        WaitingRoomScreen(
            state =
                WaitingRoomUiState.Content(
                    QueuePositionUi(
                        position = 1,
                        peopleAhead = 0,
                        phase = QueuePhase.YourTurn,
                    ),
                ),
            eventTitle = "Langit Sore — Senja Panjang Live",
        )
    }
}

@Composable
internal fun WaitingRoomClosedPreview() {
    RusuhTheme {
        WaitingRoomScreen(
            state =
                WaitingRoomUiState.Content(
                    QueuePositionUi(
                        position = 128,
                        peopleAhead = 127,
                        phase = QueuePhase.QueueClosed,
                    ),
                ),
            eventTitle = "Langit Sore — Senja Panjang Live",
        )
    }
}

@Composable
internal fun WaitingRoomLoadingPreview() {
    RusuhTheme {
        WaitingRoomScreen(state = WaitingRoomUiState.Loading, eventTitle = "Langit Sore")
    }
}

@Composable
internal fun WaitingRoomEmptyPreview() {
    RusuhTheme {
        WaitingRoomScreen(state = WaitingRoomUiState.Empty, eventTitle = "Langit Sore")
    }
}

@Composable
internal fun WaitingRoomErrorPreview() {
    RusuhTheme {
        WaitingRoomScreen(
            state = WaitingRoomUiState.Error("Koneksi terputus. Coba lagi."),
            eventTitle = "Langit Sore",
        )
    }
}
