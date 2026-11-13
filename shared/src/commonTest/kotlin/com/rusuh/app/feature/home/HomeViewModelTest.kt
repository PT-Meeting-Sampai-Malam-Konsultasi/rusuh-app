package com.rusuh.app.feature.home

import com.rusuh.app.ui.components.EventStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

    private val statuses: Map<String, EventStatus> =
        mapOf(
            "waiting" to EventStatus.WaitingRoomOpen,
            "onSale" to EventStatus.OnSale,
            "almost" to EventStatus.AlmostGone,
            "full" to EventStatus.TemporarilyFull,
            "coming" to EventStatus.ComingSoon("3 hari lagi"),
            "soldOut" to EventStatus.SoldOut(41, 12_000),
            "ended" to EventStatus.Ended("30 Okt"),
        )

    private fun TestScope.loadedViewModel(): HomeViewModel {
        val events = statuses.map { (id, status) -> HomeEventItem(id = id, title = id, status = status) }
        val viewModel = HomeViewModel(FakeHomeRepository(events))
        viewModel.dispatch(HomeIntent.LoadEvents)
        testScheduler.advanceUntilIdle()
        return viewModel
    }

    @Test
    fun tappingAQueueableEventOpensItsWaitingRoom() =
        runTest(dispatcher) {
            listOf("waiting", "onSale", "almost", "full").forEach { id ->
                val viewModel = loadedViewModel()

                viewModel.dispatch(HomeIntent.EventClicked(id))
                testScheduler.advanceUntilIdle()

                assertEquals(HomeEffect.NavigateToQueue(id), viewModel.effect.first(), id)
            }
        }

    @Test
    fun tappingAComingSoonSoldOutOrEndedEventDoesNothing() =
        runTest(dispatcher) {
            listOf("coming", "soldOut", "ended", "unknown-id").forEach { id ->
                val viewModel = loadedViewModel()

                viewModel.dispatch(HomeIntent.EventClicked(id))
                testScheduler.advanceUntilIdle()
                // The queueable tap afterwards is the first effect, so nothing came before it.
                viewModel.dispatch(HomeIntent.EventClicked("onSale"))
                testScheduler.advanceUntilIdle()

                assertEquals(HomeEffect.NavigateToQueue("onSale"), viewModel.effect.first(), "after $id")
            }
        }

    @Test
    fun tappingBeforeTheEventsLoadedDoesNothing() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(FakeHomeRepository(listOf(HomeEventItem(id = "a", title = "a"))))

            viewModel.dispatch(HomeIntent.EventClicked("a"))
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(HomeIntent.LoadEvents)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(HomeIntent.EventClicked("a"))
            testScheduler.advanceUntilIdle()

            assertEquals(HomeEffect.NavigateToQueue("a"), viewModel.effect.first())
        }

    @Test
    fun onlyTheQueueableStatusesCanQueue() {
        assertEquals(
            setOf("waiting", "onSale", "almost", "full"),
            statuses.filterValues { it.canQueue() }.keys,
        )
    }

    @Test
    fun loadEventsErrorSetsErrorStateAndEmitsShowError() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(FakeHomeRepository(error = RuntimeException("network down")))

            viewModel.dispatch(HomeIntent.LoadEvents)
            testScheduler.advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(!state.isLoading, "loading should be false after error")
            assertEquals(HomeError.LoadFailed, state.error, "error should be HomeError.LoadFailed")

            val effect = viewModel.effect.first()
            assertEquals(HomeEffect.ShowError(HomeError.LoadFailed), effect)
        }
}
