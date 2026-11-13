package com.rusuh.app.feature.eticket

import com.rusuh.app.feature.tickets.TicketsRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Loads the e-ticket for order [orderId] from the tickets repository. An unknown order, a blank id or an
 * order without issued tickets is [ETicketResult.NotFound], never an exception.
 */
class GetETicketUseCase(
    private val repository: TicketsRepository,
) {
    suspend operator fun invoke(orderId: String): ETicketResult {
        if (orderId.isBlank()) return ETicketResult.NotFound
        val order = repository.getOrderTickets(orderId)
        return if (order == null || order.tickets.isEmpty()) ETicketResult.NotFound else ETicketResult.Found(order)
    }

    /**
     * Loads the order, wrapping failures as [ETicketResult.NotFound] so the caller can decide
     * how to handle errors.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun load(orderId: String): ETicketResult =
        try {
            invoke(orderId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ETicketResult.NotFound
        }
}

/**
 * Loads the page state for the UI, turning any failure into [ETicketUiState.Error] so the screen never
 * hangs on the skeleton. Cancellation is rethrown.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
suspend fun GetETicketUseCase.loadUiState(orderId: String): ETicketUiState =
    try {
        when (val result = invoke(orderId)) {
            is ETicketResult.Found -> ETicketUiState.Content(result.order.toUi())
            ETicketResult.NotFound -> ETicketUiState.NotFound
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        ETicketUiState.Error
    }
