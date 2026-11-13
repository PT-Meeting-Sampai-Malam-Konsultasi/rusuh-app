package com.rusuh.app.feature.payment

import androidx.compose.runtime.Immutable
import com.rusuh.app.domain.model.AttendeeInfo
import com.rusuh.app.domain.model.PriceBreakdown
import com.rusuh.app.mvi.MviEffect
import com.rusuh.app.mvi.MviIntent
import com.rusuh.app.mvi.MviState

/** Length of the payment window in seconds (server-driven; 10 minutes per ADR-0005). */
const val PAYMENT_WINDOW_SECONDS = 10 * 60

/** Seconds at/below which the countdown chip switches to the warning treatment. */
const val PAYMENT_COUNTDOWN_WARN_SECONDS = 60

/**
 * MVI contract for the Payment screen.
 */
object PaymentContract {
    /**
     * UI-level state for the Payment screen.
     */
    @Immutable
    data class State(
        val ui: PaymentUiState = PaymentUiState.Loading,
        val remainingSeconds: Int = PAYMENT_WINDOW_SECONDS,
        val isSnapLaunching: Boolean = false,
        val isCancelling: Boolean = false,
        val cancelError: CancelError? = null,
    ) : MviState
}

/** User intents for the Payment screen. */
sealed interface PaymentIntent : MviIntent {
    /** Initial load of the payment summary. */
    data object Load : PaymentIntent

    /** Reload after a failed load. */
    data object Retry : PaymentIntent

    /** Navigate back. */
    data object Back : PaymentIntent

    /** Launch the Snap payment page. */
    data object OpenSnap : PaymentIntent

    /** Retry opening Snap after a 503 PAYMENT_PROVIDER_BUSY error. */
    data object RetrySnap : PaymentIntent

    /** The countdown ticked: recompute remaining time from the server clock. */
    data object Tick : PaymentIntent

    /** The app returned to the foreground after the Snap handoff. */
    data object Resumed : PaymentIntent

    /** Go to the e-ticket / Tiket tab after successful payment. */
    data object GoToTickets : PaymentIntent

    /** Queue again after payment expired. */
    data object QueueAgain : PaymentIntent

    /** Go to Home after payment expired or cancelled. */
    data object GoHome : PaymentIntent

    data object CancelBooking : PaymentIntent

    data object SnapClosed : PaymentIntent
}

/** Side effects emitted by the Payment ViewModel. */
sealed interface PaymentEffect : MviEffect {
    /** Navigate back to the previous screen. */
    data object NavigateBack : PaymentEffect

    /** Launch the Snap payment page at [url]. */
    data class LaunchSnap(val url: String) : PaymentEffect

    /** Navigate to the e-ticket screen for [orderId]. */
    data class NavigateToETicket(val orderId: String) : PaymentEffect

    /** Navigate to the Tiket tab. */
    data object NavigateToTickets : PaymentEffect

    /** Navigate to the Waiting Room to queue again. */
    data class NavigateToQueue(val eventId: String) : PaymentEffect

    /** Navigate to the Home screen. */
    data object NavigateToHome : PaymentEffect
}

/**
 * UI state variants for the Payment screen.
 */
sealed interface PaymentUiState {
    /** Loading the payment summary. */
    data object Loading : PaymentUiState

    /**
     * Ready to pay: shows the order summary and a button to open Snap.
     * Also shown when the Snap tab was closed without completing payment.
     */
    @Immutable
    data class Ready(
        val summary: PaymentSummaryUi,
        val hasOpenedSnap: Boolean = false,
        val busyError: BusyError? = null,
    ) : PaymentUiState

    /** Verifying payment after returning from Snap. */
    @Immutable
    data class Verifying(
        val summary: PaymentSummaryUi,
        val methodName: String? = null,
    ) : PaymentUiState

    /** Payment succeeded; tickets are issued. */
    @Immutable
    data class Success(
        val orderCode: String,
        val eventName: String?,
        val ticketCount: Int,
        val bookingId: String,
    ) : PaymentUiState

    /** Payment expired: the 10-minute window elapsed without confirmed payment. */
    @Immutable
    data class Expired(
        val summary: PaymentSummaryUi,
        val customerEmail: String?,
        val refundRequired: Boolean,
    ) : PaymentUiState

    /** Failed to load the payment summary. */
    @Immutable
    data class Error(val message: String) : PaymentUiState
}

enum class CancelError { NOT_CANCELLABLE, GENERIC }

/** Error state for 503 PAYMENT_PROVIDER_BUSY. */
@Immutable
data class BusyError(
    val retryAfterSeconds: Int?,
)

/**
 * UI model for the payment summary shown on the handoff and verifying screens.
 */
@Immutable
data class PaymentSummaryUi(
    val bookingId: String,
    val orderCode: String,
    val eventName: String?,
    val eventMeta: String?,
    val attendees: List<AttendeeLineUi>,
    val price: PriceBreakdownUi,
    val customerEmail: String?,
)

/** One attendee line: category name, masked NIK, unit price. */
@Immutable
data class AttendeeLineUi(
    val categoryName: String,
    val nikMasked: String,
    val priceRupiah: Long,
)

/** Price breakdown for display. */
@Immutable
data class PriceBreakdownUi(
    val ticketSubtotal: Long,
    val adminFee: Long,
    val tax: Long?,
    val total: Long,
)

// --- Mappers from domain models to UI models ---

/** Map domain [AttendeeInfo] list to [AttendeeLineUi] list, with prices from [PriceBreakdown]. */
fun List<AttendeeInfo>.toUi(price: PriceBreakdown): List<AttendeeLineUi> {
    // Build a map from categoryId to unit price
    val priceByCategory = price.lines.associate { it.categoryId to it.unitPrice }
    return map { attendee ->
        AttendeeLineUi(
            categoryName = attendee.categoryName,
            nikMasked = attendee.nikMasked,
            priceRupiah = priceByCategory[attendee.categoryId] ?: 0L,
        )
    }
}

/** Map domain [PriceBreakdown] to [PriceBreakdownUi]. */
fun PriceBreakdown.toUi(): PriceBreakdownUi =
    PriceBreakdownUi(
        ticketSubtotal = ticketSubtotal,
        adminFee = adminFee,
        tax = tax,
        total = total,
    )

/** Formats seconds as "m:ss" for the countdown chip. */
fun formatPaymentCountdown(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
