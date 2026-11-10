package com.rusuh.app.feature.tickets

/**
 * Loads the Tiket tab: guests get [MyTickets.SignedOut]; signed-in users get
 * their orders split into upcoming (soonest first) and past (latest first), or
 * [MyTickets.Empty] when they have none.
 *
 * @param isSignedIn whether an account session exists.
 *
 * shortcut: defaults to signed in until the auth state seam (AUTH-00) exists.
 */
class GetMyTicketsUseCase(
    private val repository: TicketsRepository,
    private val isSignedIn: () -> Boolean = { true },
) {
    suspend operator fun invoke(): MyTickets =
        if (isSignedIn()) classify(repository.getMyOrders()) else MyTickets.SignedOut

    private fun classify(orders: List<TicketOrder>): MyTickets {
        val (past, upcoming) = orders.partition { it.isPast }
        return if (orders.isEmpty()) {
            MyTickets.Empty
        } else {
            MyTickets.Content(
                upcoming = upcoming.sortedBy { it.startsAtEpochMillis },
                past = past.sortedByDescending { it.startsAtEpochMillis },
            )
        }
    }
}
