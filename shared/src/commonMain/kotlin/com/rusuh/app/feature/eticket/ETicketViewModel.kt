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
    private val calendarLauncher: CalendarLauncher,
) : MviViewModel<ETicketState, ETicketIntent, ETicketEffect>(ETicketState()) {
    override suspend fun reduce(intent: ETicketIntent) {
        when (intent) {
            ETicketIntent.Load, ETicketIntent.Retry -> load()
            ETicketIntent.Back -> sendEffect(ETicketEffect.NavigateBack)
            ETicketIntent.AddToCalendar -> addToCalendar()
        }
    }

    private suspend fun load() {
        setState { copy(ui = ETicketUiState.Loading) }
        val ui = getETicket.loadUiState(orderId)
        // Also store the order for calendar event creation
        val order =
            when (ui) {
                is ETicketUiState.Content -> getETicket(orderId).let { (it as? ETicketResult.Found)?.order }
                else -> null
            }
        setState { copy(ui = ui, order = order) }
    }

    private suspend fun addToCalendar() {
        val order = state.value.order ?: return
        val event = order.toCalendarEvent()
        when (calendarLauncher.launch(event)) {
            CalendarLaunchResult.Success -> sendEffect(ETicketEffect.LaunchCalendar(event))
            CalendarLaunchResult.NoCalendarApp -> sendEffect(ETicketEffect.NoCalendarApp)
        }
    }
}
