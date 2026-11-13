package com.rusuh.app.feature.home

import com.rusuh.app.analytics.AnalyticsTracker
import com.rusuh.app.analytics.ItemData
import com.rusuh.app.mvi.MviViewModel

/**
 * MVI ViewModel for the Home screen.
 *
 * Owns the state layer only: it loads events through the [HomeRepository] seam and
 * reduces [HomeIntent]s into [HomeState] / [HomeEffect]. It does not render UI.
 *
 * @param repository data seam; [StubHomeRepository] until M4 (API integration) lands.
 */
class HomeViewModel(
    private val repository: HomeRepository,
    private val tracker: AnalyticsTracker? = null,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {
    private var viewItemListSent = false

    override suspend fun reduce(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.LoadEvents -> loadEvents()
            is HomeIntent.RefreshEvents -> loadEvents()
            is HomeIntent.EventClicked -> openQueue(intent.id)
        }
    }

    /** The route decision: only a known event that can be queued for opens its Waiting Room. */
    private fun openQueue(id: String) {
        val event = currentState.events.firstOrNull { it.id == id }
        if (event != null && event.status.canQueue()) {
            tracker?.selectItem(event.toItemData())
            sendEffect(HomeEffect.NavigateToQueue(id))
        }
    }

    private suspend fun loadEvents() {
        setState { copy(isLoading = true, error = null) }
        try {
            val events = repository.getEvents()
            setState { copy(isLoading = false, events = events, error = null) }
            if (events.isNotEmpty() && !viewItemListSent) {
                viewItemListSent = true
                tracker?.viewItemList(events.map { it.toItemData() })
            }
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            val error = HomeError.LoadFailed
            setState { copy(isLoading = false, error = error) }
            sendEffect(HomeEffect.ShowError(error))
        }
    }

    private fun HomeEventItem.toItemData(): ItemData =
        ItemData(
            itemId = id,
            itemName = title,
            itemCategory = format.name,
            price = priceFrom?.let { parsePriceFrom(it) },
        )

    private fun parsePriceFrom(priceString: String): Long? = priceString.filter { it.isDigit() }.toLongOrNull()
}
