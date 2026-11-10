package com.rusuh.app.feature.tickets

import com.rusuh.app.ui.components.TicketAttendeeUi
import com.rusuh.app.ui.components.TicketOrderUi
import com.rusuh.app.ui.components.TicketPoster

/** Maps the domain result onto what [TicketsScreen] renders. */
internal fun MyTickets.toUiState(): TicketsUiState =
    when (this) {
        is MyTickets.Content -> TicketsUiState.Content(upcoming.map { it.toUi() }, past.map { it.toUi() })
        MyTickets.Empty -> TicketsUiState.Empty
        MyTickets.SignedOut -> TicketsUiState.SignedOut
    }

internal fun TicketOrder.toUi(): TicketOrderUi =
    TicketOrderUi(
        orderId = orderId,
        eventTitle = eventTitle,
        dateVenue = dateVenue,
        posterLabel = posterLabel,
        poster = if (isPast) TicketPoster.Grey else posterTone.toUi(),
        attendees = attendees.map { TicketAttendeeUi(it.category, it.holderName) },
        orderCode = orderId,
        totalLabel = totalLabel,
        dayLabel = dayLabel,
        monthLabel = monthLabel,
        countdownLabel = countdownLabel,
        isPast = isPast,
    )

private fun PosterTone.toUi(): TicketPoster =
    when (this) {
        PosterTone.Pink -> TicketPoster.Pink
        PosterTone.Blue -> TicketPoster.Blue
        PosterTone.Grey -> TicketPoster.Grey
    }
