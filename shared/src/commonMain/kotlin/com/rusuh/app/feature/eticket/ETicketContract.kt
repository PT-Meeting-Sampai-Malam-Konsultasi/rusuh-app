package com.rusuh.app.feature.eticket

import androidx.compose.runtime.Immutable
import com.rusuh.app.mvi.MviEffect
import com.rusuh.app.mvi.MviIntent
import com.rusuh.app.mvi.MviState

/** MVI state for the e-ticket page; [ui] is exactly what [ETicketScreen] renders. */
data class ETicketState(
    val ui: ETicketUiState = ETicketUiState.Loading,
    /** The order data, kept for calendar event creation after content is loaded. */
    val order: ETicketOrder? = null,
) : MviState

/** User intents on the e-ticket page. */
sealed interface ETicketIntent : MviIntent {
    /** Initial load of the order's tickets. */
    data object Load : ETicketIntent

    /** Reload after a failed load. */
    data object Retry : ETicketIntent

    /** Leave the page (the not-found action); the system back gesture is handled by navigation. */
    data object Back : ETicketIntent

    /** Add the event to the device calendar. */
    data object AddToCalendar : ETicketIntent
}

/** One-time side effects on the e-ticket page. */
sealed interface ETicketEffect : MviEffect {
    /** Return to the Tiket tab. */
    data object NavigateBack : ETicketEffect

    /** Launch the calendar with the given event data. */
    data class LaunchCalendar(val event: CalendarEvent) : ETicketEffect

    /** No calendar app is available to handle the request. */
    data object NoCalendarApp : ETicketEffect
}

/** What the e-ticket page can show. */
@Immutable
sealed interface ETicketUiState {
    data object Loading : ETicketUiState

    data class Content(val ui: ETicketUi) : ETicketUiState

    /** Loading failed; the user can retry. */
    data object Error : ETicketUiState

    /** The order does not exist or has no tickets. */
    data object NotFound : ETicketUiState
}

/** Header tint of a ticket card, chosen by category so adjacent categories read apart. */
enum class TicketTone { Blue, Pink }

/**
 * A moment on the wall clock of the event zone, split into the parts the screen formats with its own
 * strings (weekday and month names, time separator).
 *
 * @param weekdayIndex 0 = Monday .. 6 = Sunday.
 * @param monthIndex 0 = January .. 11 = December.
 */
@Immutable
data class EventMoment(
    val weekdayIndex: Int,
    val day: Int,
    val monthIndex: Int,
    val hour: Int,
    val minute: Int,
    val zoneLabel: String,
)

/** One ticket card. [position] counts from 1 of [total] tickets on the order. */
@Immutable
data class ETicketCardUi(
    val position: Int,
    val total: Int,
    val ticketId: String,
    val ticketCode: String,
    val eventName: String,
    val category: String,
    val tone: TicketTone,
    val nikMasked: String,
    val entryGate: String,
    val seating: Seating,
    val doorsOpen: EventMoment,
    /** Exact QR content; never log. */
    val qrPayload: String,
)

/** The whole page: header facts plus one card per ticket. */
@Immutable
data class ETicketUi(
    val orderId: String,
    val eventName: String,
    val venueShort: String,
    val paidAt: EventMoment,
    val orderTotal: String,
    val paymentMethod: String,
    val contactEmail: String,
    val tickets: List<ETicketCardUi>,
)
