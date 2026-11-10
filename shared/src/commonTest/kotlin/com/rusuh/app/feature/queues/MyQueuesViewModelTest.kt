package com.rusuh.app.feature.queues

import com.rusuh.app.domain.model.MyQueue
import com.rusuh.app.domain.model.MyQueuePhase
import com.rusuh.app.domain.repository.MyQueuesRepository
import com.rusuh.app.domain.usecase.GetMyQueuesUseCase
import com.rusuh.app.ui.components.QueueListPhase
import com.rusuh.app.ui.components.RusuhTab
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
    fun refreshFailureAfterContentShowsError() =
        runTest(dispatcher) {
            val vm = viewModel(listOf(queue("a")), IllegalStateException("boom"))
            vm.dispatch(MyQueuesIntent.Load)
            testScheduler.advanceUntilIdle()
            vm.dispatch(MyQueuesIntent.Refresh)
            testScheduler.advanceUntilIdle()

            assertEquals(MyQueuesPhase.Error, vm.state.value.phase)
            assertEquals(emptyList(), vm.state.value.queues)
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
        assertIs<MyQueuesUiState.Content>(MyQueuesState(MyQueuesPhase.Ready, placeholderQueues()).toUiState("e"))
    }
}
