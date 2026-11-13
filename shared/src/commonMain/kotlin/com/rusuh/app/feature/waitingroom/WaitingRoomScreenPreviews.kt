@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.waitingroom

import androidx.compose.runtime.Composable
import com.rusuh.app.ui.components.QueuePositionUi
import com.rusuh.app.ui.components.QueueProgressUi
import com.rusuh.app.ui.theme.RusuhTheme

// Preview-style sample composables wrapped in RusuhTheme with placeholder data for every Figma
// state. No @Preview annotation (no uiTooling-preview dependency in commonMain), so these are plain
// @Composable samples a platform preview harness can call. No ViewModel, repository or API.

private val sampleEvent =
    WaitingRoomEventUi(
        title = "SAMUDRA",
        subtitle = "Tur Gelombang 2026",
        dateTimeLine = "Sab, 12 Des 2026 · 19.00 WIB",
        venueLine = "Stadion Utama GBK, Jakarta",
    )

private val sampleQueue =
    QueuePositionUi(
        position = 1248,
        estimatedWaitMinutes = 4,
        ratePerMinute = 310,
        updatedSecondsAgo = 2,
        progress = QueueProgressUi(servedAtJoin = 17_400, nowServing = 18_452, myNumber = 19_700),
    )

@Composable
private fun Preview(state: WaitingRoomUiState) {
    RusuhTheme { WaitingRoomScreen(state = state, dispatch = {}) }
}

@Composable
internal fun WaitingRoomPreQueuePreview() =
    Preview(
        WaitingRoomUiState.Active(
            sampleEvent,
            WaitingRoomPhase.PreQueue(secondsUntilOpen = 522, opensAtLabel = "10.00 WIB"),
        ),
    )

@Composable
internal fun WaitingRoomWaitingPreview() =
    Preview(
        WaitingRoomUiState.Active(sampleEvent, WaitingRoomPhase.Waiting(sampleQueue)),
    )

@Composable
internal fun WaitingRoomWaitingGuestPreview() =
    Preview(WaitingRoomUiState.Active(sampleEvent, WaitingRoomPhase.Waiting(sampleQueue), isGuest = true))

@Composable
internal fun WaitingRoomPausedStockPreview() =
    Preview(
        WaitingRoomUiState.Active(sampleEvent, WaitingRoomPhase.Paused(position = 1248, reason = PauseReason.Stock)),
    )

@Composable
internal fun WaitingRoomPausedOperatorPreview() =
    Preview(
        WaitingRoomUiState.Active(sampleEvent, WaitingRoomPhase.Paused(position = 1248, reason = PauseReason.Operator)),
    )

@Composable
internal fun WaitingRoomYourTurnPreview() =
    Preview(WaitingRoomUiState.Active(sampleEvent, WaitingRoomPhase.YourTurn(myNumber = 19_700, secondsLeft = 52)))

@Composable
internal fun WaitingRoomMissedTurnPreview() =
    Preview(WaitingRoomUiState.Active(sampleEvent, WaitingRoomPhase.MissedTurn(estimateMinutes = 26)))

@Composable
internal fun WaitingRoomSoldOutPreview() = Preview(WaitingRoomUiState.Active(sampleEvent, WaitingRoomPhase.SoldOut))

@Composable
internal fun WaitingRoomYourTurnHeldPreview() =
    Preview(
        WaitingRoomUiState.Active(
            sampleEvent,
            WaitingRoomPhase.YourTurn(myNumber = 19_700, secondsLeft = 52, heldForSignIn = true),
            isGuest = true,
        ),
    )
