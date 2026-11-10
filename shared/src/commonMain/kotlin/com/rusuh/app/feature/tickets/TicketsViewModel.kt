package com.rusuh.app.feature.tickets

import com.rusuh.app.mvi.MviViewModel

/**
 * MVI ViewModel for the Tiket tab. Loads through [GetMyTicketsUseCase.loadUiState],
 * which maps failures to [TicketsUiState.Error] and rethrows cancellation, so the
 * state never stays on Loading.
 */
class TicketsViewModel(
    private val getMyTickets: GetMyTicketsUseCase,
) : MviViewModel<TicketsState, TicketsIntent, TicketsEffect>(TicketsState()) {
    override suspend fun reduce(intent: TicketsIntent) {
        when (intent) {
            TicketsIntent.Load, TicketsIntent.Retry -> load()
            is TicketsIntent.ViewETicket -> sendEffect(TicketsEffect.OpenETicket(intent.orderId))
            TicketsIntent.SignIn -> sendEffect(TicketsEffect.OpenSignIn)
            TicketsIntent.BrowseEvents -> sendEffect(TicketsEffect.GoToEvents)
        }
    }

    private suspend fun load() {
        setState { copy(ui = TicketsUiState.Loading) }
        val ui = getMyTickets.loadUiState()
        setState { copy(ui = ui) }
    }
}
