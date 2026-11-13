package com.rusuh.app.feature.waitingroom

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.rusuh.app.feature.initializeComposeResourceContext
import com.rusuh.app.feature.startScreenTestKoin
import com.rusuh.app.ui.components.QueuePositionUi
import com.rusuh.app.ui.components.QueueProgressUi
import com.rusuh.app.ui.theme.RusuhTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class WaitingRoomProgressUiTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun initializeTestEnvironment() {
        initializeComposeResourceContext()
        startScreenTestKoin()
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    @Config(qualifiers = "in")
    fun waitingTrackShowsTheJoinAndTurnLabels() {
        val event = WaitingRoomEventUi("Konser", "", "", "")
        val queue =
            QueuePositionUi(
                position = 5,
                estimatedWaitMinutes = 1,
                progress = QueueProgressUi(servedAtJoin = 10, nowServing = 50, myNumber = 100),
            )
        compose.setContent {
            RusuhTheme {
                WaitingRoomScreen(WaitingRoomUiState.Active(event, WaitingRoomPhase.Waiting(queue)), {})
            }
        }
        compose.onNodeWithText("Saat Anda bergabung").assertIsDisplayed()
        compose.onNodeWithText("Giliran Anda").assertIsDisplayed()
        compose.onNodeWithTag("queue-progress-blue-fill", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Sedang dilayani No. 50 · Nomor Anda No. 100").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "in")
    fun turnClaimingAndMissedStatesShowTheirActions() {
        val event = WaitingRoomEventUi("Konser", "", "", "")
        val state =
            mutableStateOf<WaitingRoomUiState>(
                WaitingRoomUiState.Active(event, WaitingRoomPhase.YourTurn(19_700, 52)),
            )
        compose.setContent {
            RusuhTheme {
                WaitingRoomScreen(state.value, {})
            }
        }
        compose.onAllNodesWithText("Giliran Anda!", useUnmergedTree = true, substring = false).assertCountEquals(2)
        compose.onNodeWithText("Pilih tiket sekarang →", useUnmergedTree = true, substring = false).assertIsDisplayed()

        compose.runOnIdle {
            state.value = WaitingRoomUiState.Active(event, WaitingRoomPhase.YourTurn(19_700, 52, claiming = true))
        }
        compose.onNodeWithText("Sedang masuk →", useUnmergedTree = true, substring = false).assertIsDisplayed()

        compose.runOnIdle {
            state.value = WaitingRoomUiState.Active(event, WaitingRoomPhase.MissedTurn(12))
        }
        compose.onNodeWithText("Giliran terlewat.", useUnmergedTree = true, substring = false).assertIsDisplayed()
        compose.onNodeWithText("Antre lagi →", useUnmergedTree = true, substring = false).assertExists()
    }

    @Test
    @Config(qualifiers = "en")
    fun englishTurnClaimingAndMissedCopyIsShown() {
        val event = WaitingRoomEventUi("Concert", "", "", "")
        val state =
            mutableStateOf<WaitingRoomUiState>(
                WaitingRoomUiState.Active(event, WaitingRoomPhase.YourTurn(19_700, 52)),
            )
        compose.setContent {
            RusuhTheme {
                WaitingRoomScreen(state.value, {})
            }
        }
        compose.onNodeWithText("Choose tickets now →", useUnmergedTree = true, substring = false).assertExists()
        compose.runOnIdle {
            state.value = WaitingRoomUiState.Active(event, WaitingRoomPhase.YourTurn(19_700, 52, claiming = true))
        }
        compose.onNodeWithText("Claiming →", useUnmergedTree = true, substring = false).assertExists()
        compose.runOnIdle {
            state.value = WaitingRoomUiState.Active(event, WaitingRoomPhase.MissedTurn(12))
        }
        compose.onNodeWithText("Turn missed.", useUnmergedTree = true, substring = false).assertExists()
        compose.onNodeWithText("Queue again →", useUnmergedTree = true, substring = false).assertExists()
    }
}
