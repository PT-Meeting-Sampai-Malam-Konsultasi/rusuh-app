@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rusuh.app.ui.components.SeatAvailability
import com.rusuh.app.ui.components.SeatCategoryUi
import com.rusuh.app.ui.theme.RusuhTheme
import kotlinx.coroutines.delay

// Preview-style sample composables with hardcoded placeholder data (Figma "Samudra" content).
// No @Preview annotation: the project does not depend on compose uiTooling-preview in commonMain
// (same convention as WaitingRoomScreenPreviews). Placeholder values only — no ViewModel/API.

internal val sampleDetailContent =
    DetailContent(
        eventTitle = "Samudra",
        eventSubtitle = "Tur Gelombang 2026",
        eventMeta = "Sab, 12 Des 2026 · 19.00 WIB · Stadion Utama GBK, Jakarta",
        categories =
            listOf(
                SeatCategoryUi(
                    "fest-a",
                    "Festival A",
                    "Berdiri · paling dekat panggung",
                    1_250_000,
                    SeatAvailability.SoldOut,
                ),
                SeatCategoryUi(
                    "fest-b",
                    "Festival B",
                    "Berdiri · di belakang Festival A",
                    850_000,
                    SeatAvailability.Available,
                ),
                SeatCategoryUi(
                    "tribun-bawah",
                    "Tribun Bawah",
                    "Duduk · bebas pilih kursi di tribun bawah",
                    650_000,
                    SeatAvailability.AlmostGone,
                ),
                SeatCategoryUi(
                    "tribun-atas",
                    "Tribun Atas",
                    "Duduk · bebas pilih kursi di tribun atas",
                    450_000,
                    SeatAvailability.Available,
                ),
            ),
        accountName = "Nadia Saputri",
        accountEmail = "nadia.saputri@gmail.com",
        adminFeeRupiah = 10_000,
    )

@Composable
internal fun DetailBookingPreview() {
    RusuhTheme { DetailScreen(DetailUiState.Content(sampleDetailContent, remainingSeconds = 252), dispatch = {}) }
}

@Composable
internal fun DetailExpiredPreview() {
    RusuhTheme { DetailScreen(DetailUiState.Expired(sampleDetailContent), dispatch = {}) }
}

@Composable
internal fun DetailLoadingPreview() {
    RusuhTheme { DetailScreen(DetailUiState.Loading, dispatch = {}) }
}

/**
 * Placeholder host standing in for the future DetailViewModel (M3): ticks the 5-minute countdown
 * locally, flips to [DetailUiState.Expired] at zero and restarts on "Antre lagi". UI-only.
 */
@Composable
internal fun DetailPlaceholderHost(onBack: () -> Unit = {}) {
    var state: DetailUiState by remember {
        mutableStateOf(DetailUiState.Content(sampleDetailContent, ORDER_WINDOW_SECONDS))
    }
    LaunchedEffect(state is DetailUiState.Content) {
        while (state is DetailUiState.Content) {
            delay(1_000)
            val current = state as? DetailUiState.Content ?: break
            state =
                if (current.remainingSeconds <= 1) {
                    DetailUiState.Expired(current.detail)
                } else {
                    current.copy(remainingSeconds = current.remainingSeconds - 1)
                }
        }
    }
    RusuhTheme {
        DetailScreen(
            state = state,
            dispatch = { intent ->
                when (intent) {
                    DetailIntent.Back, DetailIntent.GoHome -> onBack()
                    DetailIntent.QueueAgain ->
                        state = DetailUiState.Content(sampleDetailContent, ORDER_WINDOW_SECONDS)
                    DetailIntent.Proceed, DetailIntent.ChangeEmail -> Unit // TODO(M3/M4)
                }
            },
        )
    }
}
