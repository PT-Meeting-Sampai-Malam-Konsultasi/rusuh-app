package com.rusuh.app.feature.queues

import com.rusuh.app.domain.model.MyQueue
import com.rusuh.app.domain.model.MyQueuePhase
import com.rusuh.app.domain.model.OrderInProgress
import com.rusuh.app.domain.repository.MyQueuesRepository
import com.rusuh.app.domain.usecase.GetMyQueuesUseCase
import com.rusuh.app.ui.components.QueueListPhase
import com.rusuh.app.ui.components.RusuhTab
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MyQueuesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Serves [results] in order; a Throwable result is thrown. The last one repeats. */
    private class FakeRepository(private vararg val results: Any) : MyQueuesRepository {
        var calls = 0

        override suspend fun getMyQueues(): List<MyQueue> {
            val result = results[minOf(calls++, results.lastIndex)]
            if (result is Throwable) throw result
            @Suppress("UNCHECKED_CAST")
            return result as List<MyQueue>
        }
    }

    /** Each call suspends on the next deferred, so tests control response order. */
    private class GatedRepository(private val gates: List<CompletableDeferred<List<MyQueue>>>) : MyQueuesRepository {
        private var calls = 0

        override suspend fun getMyQueues(): List<MyQueue> = gates[calls++].await()
    }

    private fun queue(
        id: String,
        position: Int = 5,
        phase: MyQueuePhase = MyQueuePhase.Live,
    ) = MyQueue(id, "Event $id", "date", position, null, phase, updatedAgoSeconds = 1)

    private fun viewModel(vararg results: Any) = MyQueuesViewModel(GetMyQueuesUseCase(FakeRepository(*results)))

    @Test
    fun startsInLoading() {
        assertEquals(MyQueuesPhase.Loading, viewModel(emptyList<MyQueue>()).state.value.phase)
    }

    @Test
    fun loadWithQueuesShowsContent() =
        runTest(dispatcher) {
            val vm = viewModel(listOf(queue("a"), queue("b")))
            vm.dispatch(MyQueuesIntent.Load)
            testScheduler.advanceUntilIdle()

            assertEquals(MyQueuesPhase.Ready, vm.state.value.phase)
            assertEquals(listOf("a", "b"), vm.state.value.queues.map { it.eventId })
        }

    @Test
    fun loadWithNoQueuesShowsEmpty() =
        runTest(dispatcher) {
            val vm = viewModel(emptyList<MyQueue>())
            vm.dispatch(MyQueuesIntent.Load)
            testScheduler.advanceUntilIdle()

            assertEquals(MyQueuesPhase.Empty, vm.state.value.phase)
        }

    @Test
    fun onlyInvalidRowsShowsEmptyNotCrash() =
        runTest(dispatcher) {
            val vm = viewModel(listOf(queue("", position = 3), queue("x", position = 0)))
            vm.dispatch(MyQueuesIntent.Load)
            testScheduler.advanceUntilIdle()

            assertEquals(MyQueuesPhase.Empty, vm.state.value.phase)
        }

    @Test
    fun failureShowsErrorThenRetryRecovers() =
        runTest(dispatcher) {
            val vm = viewModel(IllegalStateException("boom"), listOf(queue("a")))
            vm.dispatch(MyQueuesIntent.Load)
            testScheduler.advanceUntilIdle()
            assertEquals(MyQueuesPhase.Error, vm.state.value.phase)

            vm.dispatch(MyQueuesIntent.Retry)
            testScheduler.advanceUntilIdle()
            assertEquals(MyQueuesPhase.Ready, vm.state.value.phase)
        }

    @Test
    fun refreshFailureFromReadyKeepsRowsAndEmitsShowError() =
        runTest(dispatcher) {
            val vm = viewModel(listOf(queue("a")), IllegalStateException("boom"))
            vm.dispatch(MyQueuesIntent.Load)
            testScheduler.advanceUntilIdle()
            vm.dispatch(MyQueuesIntent.Refresh)
            testScheduler.advanceUntilIdle()

            assertEquals(MyQueuesPhase.Ready, vm.state.value.phase)
            assertEquals(listOf("a"), vm.state.value.queues.map { it.eventId })
            assertEquals(MyQueuesEffect.ShowError(MyQueuesMessage.RefreshFailed), vm.effect.first())
        }

    @Test
    fun refreshFromReadyStaysReadyWhileLoadingThenReplacesRows() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<List<MyQueue>>()
            val repo = GatedRepository(listOf(CompletableDeferred(listOf(queue("a"))), gate))
            val vm = MyQueuesViewModel(GetMyQueuesUseCase(repo))
            vm.dispatch(MyQueuesIntent.Load)
            testScheduler.advanceUntilIdle()
            vm.dispatch(MyQueuesIntent.Refresh)
            testScheduler.advanceUntilIdle()

            assertEquals(MyQueuesPhase.Ready, vm.state.value.phase)
            assertEquals(listOf("a"), vm.state.value.queues.map { it.eventId })

            gate.complete(listOf(queue("b"), queue("c")))
            testScheduler.advanceUntilIdle()
            assertEquals(listOf("b", "c"), vm.state.value.queues.map { it.eventId })
        }

    @Test
    fun lastRequestWinsWhenLoadsOverlap() =
        runTest(dispatcher) {
            val first = CompletableDeferred<List<MyQueue>>()
            val second = CompletableDeferred<List<MyQueue>>()
            val vm = MyQueuesViewModel(GetMyQueuesUseCase(GatedRepository(listOf(first, second))))
            vm.dispatch(MyQueuesIntent.Load)
            testScheduler.advanceUntilIdle()
            vm.dispatch(MyQueuesIntent.Retry)
            testScheduler.advanceUntilIdle()

            second.complete(listOf(queue("new")))
            testScheduler.advanceUntilIdle()
            first.complete(listOf(queue("old")))
            testScheduler.advanceUntilIdle()

            assertEquals(listOf("new"), vm.state.value.queues.map { it.eventId })
        }

    @Test
    fun cancellationIsNotTurnedIntoError() =
        runTest(dispatcher) {
            val vm = viewModel(CancellationException("cancelled"))
            vm.dispatch(MyQueuesIntent.Load)
            testScheduler.advanceUntilIdle()

            assertEquals(MyQueuesPhase.Loading, vm.state.value.phase)
        }

    @Test
    fun loadEmitsNoEffect() =
        runTest(dispatcher) {
            val vm = viewModel(listOf(queue("a")))
            vm.dispatch(MyQueuesIntent.Load)
            testScheduler.advanceUntilIdle()

            val pending = vm.effect.produceIn(this)
            testScheduler.advanceUntilIdle()
            assertNull(pending.tryReceive().getOrNull())
            pending.cancel()
        }

    @Test
    fun intentsEmitEffects() =
        runTest(dispatcher) {
            val vm = viewModel(emptyList<MyQueue>())
            vm.dispatch(MyQueuesIntent.OpenQueue("evt-1"))
            vm.dispatch(MyQueuesIntent.SelectTab(RusuhTab.Tiket))
            vm.dispatch(MyQueuesIntent.BrowseEvents)
            testScheduler.advanceUntilIdle()

            assertEquals(MyQueuesEffect.NavigateToWaitingRoom("evt-1"), vm.effect.first())
            assertEquals(MyQueuesEffect.SwitchTab(RusuhTab.Tiket), vm.effect.first())
            assertEquals(MyQueuesEffect.GoToEvents, vm.effect.first())
            val rest = vm.effect.produceIn(this)
            testScheduler.advanceUntilIdle()
            assertNull(rest.tryReceive().getOrNull(), "each effect is delivered exactly once")
            rest.cancel()
        }

    @Test
    fun continueBookingUsesTheStoredOrderWindow() =
        runTest(dispatcher) {
            val order = OrderInProgress("evt-order", remainingMillis = 60_000)
            val repository =
                object : MyQueuesRepository {
                    override suspend fun getMyQueues(): List<MyQueue> = emptyList()

                    override suspend fun getOrdersInProgress(): List<OrderInProgress> = listOf(order)
                }
            val vm = MyQueuesViewModel(GetMyQueuesUseCase(repository))
            vm.dispatch(MyQueuesIntent.Load)
            testScheduler.runCurrent()

            assertEquals(listOf(order), vm.state.value.ordersInProgress)
            vm.dispatch(MyQueuesIntent.ContinueOrder(order))
            testScheduler.runCurrent()
            assertEquals(MyQueuesEffect.ContinueBooking("evt-order"), vm.effect.first())
        }

    @Test
    fun mapperDropsInvalidAndDuplicateRowsWithoutThrowing() {
        val rows =
            listOf(
                queue("ok"),
                queue(""),
                queue("waiting-zero", position = 0),
                queue("ok"),
                queue("turn", position = 0, phase = MyQueuePhase.YourTurn),
                queue("neg-eta").copy(etaMinutes = -1),
                queue("neg-age").copy(updatedAgoSeconds = -5),
            )
        val ui = rows.toUiItems()

        assertEquals(listOf("ok", "turn"), ui.map { it.eventId })
        assertEquals(QueueListPhase.YourTurn, ui.last().phase)
    }

    @Test
    fun uiStateMapping() {
        assertIs<MyQueuesUiState.Loading>(MyQueuesState().toUiState("e"))
        assertEquals(MyQueuesUiState.Empty, MyQueuesState(MyQueuesPhase.Empty).toUiState("e"))
        assertEquals(MyQueuesUiState.Error("e"), MyQueuesState(MyQueuesPhase.Error).toUiState("e"))
        assertEquals(
            MyQueuesUiState.Content(placeholderQueues()),
            MyQueuesState(MyQueuesPhase.Ready, placeholderQueues()).toUiState("e"),
        )
    }
}
