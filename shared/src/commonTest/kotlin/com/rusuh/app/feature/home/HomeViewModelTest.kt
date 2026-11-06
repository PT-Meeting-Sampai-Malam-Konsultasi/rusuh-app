package com.rusuh.app.feature.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        // MviViewModel launches on Dispatchers.Main; redirect it to the test dispatcher.
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeHomeRepository(
        private val events: List<HomeEventItem> = emptyList(),
        private val error: Throwable? = null,
    ) : HomeRepository {
        override suspend fun getEvents(): List<HomeEventItem> {
            error?.let { throw it }
            return events
        }
    }

    @Test
    fun loadEventsTransitionsFromLoadingToLoaded() =
        runTest(dispatcher) {
            val events = listOf(HomeEventItem(id = "1", title = "Konser"))
            val viewModel = HomeViewModel(FakeHomeRepository(events = events))

            viewModel.dispatch(HomeIntent.LoadEvents)
            testScheduler.advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(!state.isLoading, "loading should be false once loaded")
            assertEquals(events, state.events, "events should be populated")
            assertNull(state.error, "error should be null on success")
        }

    @Test
    fun refreshEventsReloadsEvents() =
        runTest(dispatcher) {
            val events = listOf(HomeEventItem(id = "2", title = "Festival"))
            val viewModel = HomeViewModel(FakeHomeRepository(events = events))

            viewModel.dispatch(HomeIntent.RefreshEvents)
            testScheduler.advanceUntilIdle()

            assertEquals(events, viewModel.state.value.events)
        }

    @Test
    fun eventClickedEmitsNavigateToEventEffect() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(FakeHomeRepository())

            viewModel.dispatch(HomeIntent.EventClicked(id = "42"))
            testScheduler.advanceUntilIdle()

            val effect = viewModel.effect.first()
            assertEquals(HomeEffect.NavigateToEvent(id = "42"), effect)
        }

    @Test
    fun loadEventsErrorSetsErrorStateAndEmitsShowError() =
        runTest(dispatcher) {
            val message = "network down"
            val viewModel = HomeViewModel(FakeHomeRepository(error = RuntimeException(message)))

            viewModel.dispatch(HomeIntent.LoadEvents)
            testScheduler.advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(!state.isLoading, "loading should be false after error")
            assertEquals(message, state.error, "error message should be stored in state")

            val effect = viewModel.effect.first()
            assertEquals(HomeEffect.ShowError(message), effect)
        }
}
