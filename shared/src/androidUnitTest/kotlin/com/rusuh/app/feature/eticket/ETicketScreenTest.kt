package com.rusuh.app.feature.eticket

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.rusuh.app.feature.initializeComposeResourceContext
import com.rusuh.app.feature.startScreenTestKoin
import com.rusuh.app.ui.theme.RusuhTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ETicketScreenTest {
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
