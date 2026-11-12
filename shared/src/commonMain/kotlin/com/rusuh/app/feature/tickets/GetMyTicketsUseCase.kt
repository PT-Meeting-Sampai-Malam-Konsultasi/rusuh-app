package com.rusuh.app.feature.tickets

import kotlin.coroutines.cancellation.CancellationException

/**
 * Loads the Tiket tab: guests get [MyTickets.SignedOut]; signed-in users get
 * their orders split into upcoming (soonest first) and past (latest first), or
 * [MyTickets.Empty] when they have none.
 *
 * @param isSignedIn whether an account session exists; suspend so it can wait for the cold-start session restore
 * (`SessionManager.awaitSignedIn`, wired in the app module).
 *
 * shortcut: the default is signed in, for tests and previews that have no session.
 */
class GetMyTicketsUseCase(
    private val repository: TicketsRepository,
    private val isSignedIn: suspend () -> Boolean = { true },
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

/**
 * Loads the tab state for the UI, turning any failure into [TicketsUiState.Error]
 * so the screen never hangs on the skeleton. Cancellation is rethrown.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
suspend fun GetMyTicketsUseCase.loadUiState(): TicketsUiState =
    try {
        invoke().toUiState()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        TicketsUiState.Error
    }
