package com.rusuh.app.feature.eticket

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.rusuh.app.ui.theme.RusuhTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ETicketScreenTest {
    @get:Rule val compose = createComposeRule()

    @Before fun initializeTestEnvironment() {
        com.rusuh.app.feature.initializeComposeResourceContext()
        com.rusuh.app.feature.startScreenTestKoin()
    }

    @Test fun contentShowsTicketsAndQrDescriptions() {
        val card = sampleETicketOrder(1).toUi().tickets.first()
        compose.setContent { RusuhTheme { ETicketCard(card) } }
        compose.onAllNodesWithContentDescription("QR code for ticket SMD-FB-04827", useUnmergedTree = true)
            .assertCountEquals(2)
    }

    @Test fun errorStateShowsRetryCopy() {
        compose.setContent { RusuhTheme { ETicketScreen(ETicketUiState.Error) } }
        compose.onNodeWithText("E-ticket could not be loaded").assertIsDisplayed()
    }
}
