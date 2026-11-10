package com.rusuh.app.feature.tickets

/** Stand-in poster artwork tone until the contract carries a poster image. */
enum class PosterTone { Pink, Blue, Grey }

/** One ticket holder on a purchased order (API `Ticket`: category + holder name). */
data class TicketAttendee(
    val category: String,
    val holderName: String,
)

/**
 * A purchased order (one booking), carrying display-ready event facts.
 *
 * shortcut: the real `GET /tickets` returns flat tickets with no event title or
 * date, so the backend contract for event facts and pre-formatted labels is still
 * open. Fields here mirror what the Tiket tab shows; adjust once BE is known.
 *
 * @param startsAtEpochMillis event start, used only to order upcoming (soonest
 *   first) and past (latest first) orders.
 */
data class TicketOrder(
    val orderId: String,
    val eventTitle: String,
    val dateVenue: String,
    val posterLabel: String,
    val posterTone: PosterTone = PosterTone.Pink,
    val attendees: List<TicketAttendee>,
    val totalLabel: String,
    val dayLabel: String,
    val monthLabel: String,
    val startsAtEpochMillis: Long,
    val countdownLabel: String? = null,
    val isPast: Boolean = false,
)

/** What the Tiket tab can show, produced by [GetMyTicketsUseCase]. */
sealed interface MyTickets {
    /** Signed in with orders; [upcoming] soonest first, [past] latest first. */
    data class Content(
        val upcoming: List<TicketOrder>,
        val past: List<TicketOrder>,
    ) : MyTickets

    /** Signed in, nothing paid yet. */
    data object Empty : MyTickets

    /** Tickets belong to the account, so a guest must sign in first. */
    data object SignedOut : MyTickets
}
