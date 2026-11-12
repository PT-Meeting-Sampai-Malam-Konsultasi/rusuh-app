package com.rusuh.app.feature.tickets

import com.rusuh.app.feature.eticket.ETicketOrder
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
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class TicketsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FlakyRepository(private var failures: Int) : TicketsRepository {
        override suspend fun getMyOrders(): List<TicketOrder> {
            if (failures > 0) {
                failures--
                error("offline")
            }
            return StubTicketsRepository().getMyOrders()
        }

        override suspend fun getOrderTickets(orderId: String): ETicketOrder? = null
    }

    private fun viewModel(
        repository: TicketsRepository = StubTicketsRepository(),
        signedIn: Boolean = true,
    ) = TicketsViewModel(GetMyTicketsUseCase(repository, isSignedIn = { signedIn }))

    @Test
    fun startsInLoading() =
        runTest(dispatcher) {
            assertEquals(TicketsUiState.Loading, viewModel().state.value.ui)
        }

    @Test
    fun loadShowsContent() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dispatch(TicketsIntent.Load)
            testScheduler.advanceUntilIdle()
            val ui = assertIs<TicketsUiState.Content>(vm.state.value.ui)
            assertEquals(2, ui.upcoming.size)
            assertEquals(1, ui.past.size)
        }

    @Test
    fun loadShowsEmpty() =
        runTest(dispatcher) {
            val vm = viewModel(StubTicketsRepository(StubTicketsJson.EMPTY))
            vm.dispatch(TicketsIntent.Load)
            testScheduler.advanceUntilIdle()
            assertEquals(TicketsUiState.Empty, vm.state.value.ui)
        }

    @Test
    fun loadShowsSignedOut() =
        runTest(dispatcher) {
            val vm = viewModel(signedIn = false)
            vm.dispatch(TicketsIntent.Load)
            testScheduler.advanceUntilIdle()
            assertEquals(TicketsUiState.SignedOut, vm.state.value.ui)
        }

    @Test
    fun errorThenRetryRecovers() =
        runTest(dispatcher) {
            val vm = viewModel(FlakyRepository(failures = 1))
            vm.dispatch(TicketsIntent.Load)
            testScheduler.advanceUntilIdle()
            assertEquals(TicketsUiState.Error, vm.state.value.ui)

            vm.dispatch(TicketsIntent.Retry)
            testScheduler.advanceUntilIdle()
            assertIs<TicketsUiState.Content>(vm.state.value.ui)
        }

    @Test
    fun viewETicketEmitsOpenETicket() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dispatch(TicketsIntent.ViewETicket("RSH-1"))
            testScheduler.advanceUntilIdle()
            assertEquals(TicketsEffect.OpenETicket("RSH-1"), vm.effect.first())
        }

    @Test
    fun signInEmitsOpenSignIn() =
        runTest(dispatcher) {
            val vm = viewModel(signedIn = false)
            vm.dispatch(TicketsIntent.SignIn)
            testScheduler.advanceUntilIdle()
            assertEquals(TicketsEffect.OpenSignIn, vm.effect.first())
        }

    @Test
    fun browseEventsEmitsGoToEvents() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dispatch(TicketsIntent.BrowseEvents)
            testScheduler.advanceUntilIdle()
            assertEquals(TicketsEffect.GoToEvents, vm.effect.first())
        }
}
