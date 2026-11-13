package com.rusuh.app.feature.eticket

import com.rusuh.app.analytics.AnalyticsEvents
import com.rusuh.app.analytics.AnalyticsTracker
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
    private val tracker: AnalyticsTracker? = null,
) : MviViewModel<ETicketState, ETicketIntent, ETicketEffect>(ETicketState()) {
    private var viewTicketSent = false

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

        if (order != null && !viewTicketSent) {
            viewTicketSent = true
            tracker?.viewTicket(orderId, order.tickets.size)
        }
    }

    private suspend fun addToCalendar() {
        val order = state.value.order ?: return
        val event = order.toCalendarEvent()
        when (calendarLauncher.launch(event)) {
            CalendarLaunchResult.Success -> {
                tracker?.ticketAddCalendar(orderId, AnalyticsEvents.VIA_DEVICE)
                sendEffect(ETicketEffect.LaunchCalendar(event))
            }
            CalendarLaunchResult.NoCalendarApp -> sendEffect(ETicketEffect.NoCalendarApp)
        }
    }
}
