@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rusuh.app.data.clock.MonotonicServerClock
import com.rusuh.app.data.repository.StubDetailData
import com.rusuh.app.data.repository.StubDetailRepository
import com.rusuh.app.domain.usecase.CreateBookingUseCase
import com.rusuh.app.domain.usecase.GetDetailUseCase
import com.rusuh.app.domain.usecase.ValidateAttendeeUseCase
import com.rusuh.app.ui.theme.RusuhTheme

// Preview-style sample composables with hardcoded placeholder data (Figma "Samudra" content).
// No @Preview annotation: the project does not depend on compose uiTooling-preview in commonMain
// (same convention as WaitingRoomScreenPreviews). Placeholder values only — no ViewModel/API.

internal val sampleDetailContent = StubDetailData.offering(expiresAtMillis = 0L).toDetailContent()

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
 * Preview host wiring the real [DetailViewModel] to the stub repository (no API): interactive
 * steppers, attendee forms and a live 5-minute countdown.
 */
@Composable
internal fun DetailPlaceholderHost(onBack: () -> Unit = {}) {
    val viewModel =
        remember {
            val clock = MonotonicServerClock()
            val repository = StubDetailRepository(clock)
            DetailViewModel(
                eventId = "samudra",
                getDetail = GetDetailUseCase(repository),
                validateAttendee = ValidateAttendeeUseCase(repository),
                createBooking = CreateBookingUseCase(repository),
                serverClock = clock,
            )
        }
    RusuhTheme {
        DetailRoute(
            viewModel = viewModel,
            onBack = onBack,
            onHome = onBack,
            onQueue = {},
            onPayment = {},
        )
    }
}
