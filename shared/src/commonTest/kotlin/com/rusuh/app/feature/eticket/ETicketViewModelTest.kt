package com.rusuh.app.feature.eticket

import com.rusuh.app.feature.tickets.StubTicketsRepository
import com.rusuh.app.feature.tickets.TicketOrder
import com.rusuh.app.feature.tickets.TicketsRepository
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
class ETicketViewModelTest {
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
        private val stub = StubTicketsRepository()

        override suspend fun getMyOrders(): List<TicketOrder> = stub.getMyOrders()

        override suspend fun getOrderTickets(orderId: String): ETicketOrder? {
            if (failures > 0) {
                failures--
                error("offline")
            }
            return stub.getOrderTickets(orderId)
        }
    }

    private fun viewModel(
        orderId: String = "RSH-26-084731",
        repository: TicketsRepository = StubTicketsRepository(),
    ) = ETicketViewModel(orderId, GetETicketUseCase(repository))

    @Test
    fun startsInLoading() =
        runTest(dispatcher) {
            assertEquals(ETicketUiState.Loading, viewModel().state.value.ui)
        }

    @Test
    fun loadShowsThreeTickets() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dispatch(ETicketIntent.Load)
            testScheduler.advanceUntilIdle()
            val ui = assertIs<ETicketUiState.Content>(vm.state.value.ui)
            assertEquals(3, ui.ui.tickets.size)
        }

    @Test
    fun unknownOrderShowsNotFound() =
        runTest(dispatcher) {
            val vm = viewModel(orderId = "RSH-NOPE")
            vm.dispatch(ETicketIntent.Load)
            testScheduler.advanceUntilIdle()
            assertEquals(ETicketUiState.NotFound, vm.state.value.ui)
        }

    @Test
    fun loadingIsVisibleUntilTheDispatcherRuns() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dispatch(ETicketIntent.Load)
            assertEquals(ETicketUiState.Loading, vm.state.value.ui)
            testScheduler.advanceUntilIdle()
            assertIs<ETicketUiState.Content>(vm.state.value.ui)
        }

    @Test
    fun errorThenRetryRecovers() =
        runTest(dispatcher) {
            val vm = viewModel(repository = FlakyRepository(failures = 1))
            vm.dispatch(ETicketIntent.Load)
            testScheduler.advanceUntilIdle()
            assertEquals(ETicketUiState.Error, vm.state.value.ui)

            vm.dispatch(ETicketIntent.Retry)
            testScheduler.advanceUntilIdle()
            assertIs<ETicketUiState.Content>(vm.state.value.ui)
        }

    @Test
    fun backEmitsNavigateBack() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dispatch(ETicketIntent.Back)
            testScheduler.advanceUntilIdle()
            assertEquals(ETicketEffect.NavigateBack, vm.effect.first())
        }
}
