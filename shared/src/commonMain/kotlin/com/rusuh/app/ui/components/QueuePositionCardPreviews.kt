@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.ui.components

import androidx.compose.runtime.Composable
import com.rusuh.app.ui.theme.RusuhTheme

// Preview-style sample composables wrapped in RusuhTheme with hardcoded
// placeholder data. No @Preview annotation: the project does not depend on
// compose uiTooling-preview in commonMain (EventCard follows the same
// convention), so these are plain @Composable samples a platform preview
// harness can call. Placeholder values only — no state, no API.

@Composable
internal fun QueuePositionCardWaitingPreview() {
    RusuhTheme {
        QueuePositionCard(
            data =
                QueuePositionUi(
                    position = 42,
                    peopleAhead = 41,
                    phase = QueuePhase.Waiting,
                    estimatedWait = "~8 menit",
                ),
        )
    }
}

@Composable
internal fun QueuePositionCardYourTurnPreview() {
    RusuhTheme {
        QueuePositionCard(
            data =
                QueuePositionUi(
                    position = 1,
                    peopleAhead = 0,
                    phase = QueuePhase.YourTurn,
                    estimatedWait = null,
                ),
        )
    }
}

@Composable
internal fun QueuePositionCardClosedPreview() {
    RusuhTheme {
        QueuePositionCard(
            data =
                QueuePositionUi(
                    position = 128,
                    peopleAhead = 127,
                    phase = QueuePhase.QueueClosed,
                    estimatedWait = null,
                ),
        )
    }
}
