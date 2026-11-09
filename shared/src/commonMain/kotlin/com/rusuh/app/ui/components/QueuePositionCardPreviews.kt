@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.RusuhTheme

// Preview-style sample composables wrapped in RusuhTheme with placeholder data. No @Preview
// annotation: the project does not depend on compose uiTooling-preview in commonMain, so these are
// plain @Composable samples a platform preview harness can call. No state, no API.

@Composable
internal fun QueuePositionCardWaitingPreview() {
    RusuhTheme {
        QueuePositionCard(
            data =
                QueuePositionUi(
                    position = 1248,
                    estimatedWaitMinutes = 4,
                    ratePerMinute = 310,
                    updatedSecondsAgo = 2,
                    progress = QueueProgressUi(servedAtJoin = 17_400, nowServing = 18_452, myNumber = 19_700),
                ),
            modifier = Modifier.padding(RusuhSpacing.xl),
        )
    }
}

@Composable
internal fun QueuePositionCardPausedPreview() {
    RusuhTheme {
        QueuePositionCard(
            data = QueuePositionUi(position = 1248, phase = QueuePhase.Paused),
            modifier = Modifier.padding(RusuhSpacing.xl),
        )
    }
}

@Composable
internal fun QueuePositionCardFrontOfLinePreview() {
    RusuhTheme {
        QueuePositionCard(
            data = QueuePositionUi(position = 1, estimatedWaitMinutes = 1, ratePerMinute = 310, updatedSecondsAgo = 0),
            modifier = Modifier.padding(RusuhSpacing.xl),
        )
    }
}
