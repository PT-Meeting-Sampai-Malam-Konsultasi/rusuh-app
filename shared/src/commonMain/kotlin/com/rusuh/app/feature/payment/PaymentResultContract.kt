package com.rusuh.app.feature.payment

import androidx.compose.runtime.Immutable
import com.rusuh.app.mvi.MviEffect
import com.rusuh.app.mvi.MviIntent
import com.rusuh.app.mvi.MviState

/**
 * MVI contract for the Payment Result screen.
 *
 * This screen is reached via App Link / Universal Link after the user completes (or abandons)
 * payment on the Midtrans Snap page. It NEVER trusts the URL: it only takes the bookingId
 * and fetches the actual status from the server.
 */
object PaymentResultContract {
    @Immutable
    data class State(
        val ui: PaymentResultUiState = PaymentResultUiState.Loading,
    ) : MviState
}

/** User intents for the Payment Result screen. */
sealed interface PaymentResultIntent : MviIntent {
    /** Start fetching the payment status. */
    data object Load : PaymentResultIntent

    /** Retry after a failed load. */
    data object Retry : PaymentResultIntent

    /** Go to the e-ticket / Tiket tab after successful payment. */
    data object GoToTickets : PaymentResultIntent

    /** Queue again after payment expired. */
    data object QueueAgain : PaymentResultIntent

    /** Go to Home. */
    data object GoHome : PaymentResultIntent
}

/** Side effects emitted by the Payment Result ViewModel. */
sealed interface PaymentResultEffect : MviEffect {
    /** Navigate to the Tiket tab. */
    data object NavigateToTickets : PaymentResultEffect

    /** Navigate to Home. */
    data object NavigateToHome : PaymentResultEffect
}

/**
 * UI state variants for the Payment Result screen.
 */
sealed interface PaymentResultUiState {
    /** Fetching the payment status. */
    data object Loading : PaymentResultUiState

    /** Payment succeeded; tickets are issued. */
    @Immutable
    data class Success(
        val orderCode: String,
        val eventName: String?,
        val ticketCount: Int,
        val bookingId: String,
    ) : PaymentResultUiState

    /** Payment expired or cancelled. */
    @Immutable
    data class Expired(
        val orderCode: String?,
        val customerEmail: String?,
        val refundRequired: Boolean,
    ) : PaymentResultUiState

    /** Payment still pending (user closed Snap before completing). */
    @Immutable
    data class Pending(
        val bookingId: String,
    ) : PaymentResultUiState

    /** Failed to fetch status (network error, invalid booking, etc.). */
    @Immutable
    data class Error(val message: String) : PaymentResultUiState
}
