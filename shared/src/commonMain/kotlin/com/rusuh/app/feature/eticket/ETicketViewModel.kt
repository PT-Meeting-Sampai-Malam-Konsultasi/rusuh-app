package com.rusuh.app.feature.eticket

import com.rusuh.app.mvi.MviViewModel

/**
 * MVI ViewModel for the e-ticket page of order [orderId]. Loads through [GetETicketUseCase.loadUiState],
 * which maps failures to [ETicketUiState.Error] and rethrows cancellation, so the state never stays on
 * Loading.
 */
class ETicketViewModel(
    private val orderId: String,
    private val getETicket: GetETicketUseCase,
) : MviViewModel<ETicketState, ETicketIntent, ETicketEffect>(ETicketState()) {
    override suspend fun reduce(intent: ETicketIntent) {
        when (intent) {
            ETicketIntent.Load, ETicketIntent.Retry -> load()
            ETicketIntent.Back -> sendEffect(ETicketEffect.NavigateBack)
        }
    }

    private suspend fun load() {
        setState { copy(ui = ETicketUiState.Loading) }
        val ui = getETicket.loadUiState(orderId)
        setState { copy(ui = ui) }
    }
}
