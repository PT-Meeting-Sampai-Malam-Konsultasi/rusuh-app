package com.rusuh.app.feature.waitingroom

import com.rusuh.app.feature.waitingroom.data.repository.StubWaitingRoomRepository
import com.rusuh.app.feature.waitingroom.domain.model.QueuePlace
import com.rusuh.app.feature.waitingroom.domain.repository.WaitingRoomRepository
import com.rusuh.app.feature.waitingroom.domain.usecase.ObserveQueuePlaceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WaitingRoomViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: WaitingRoomRepository = StubWaitingRoomRepository(STEP)) =
        WaitingRoomViewModel("evt", ObserveQueuePlaceUseCase(repository))

    private fun WaitingRoomViewModel.phase(): WaitingRoomPhase =
        assertIs<WaitingRoomUiState.Active>(
            state.value.ui,
        ).phase

    private fun TestScope.step(vm: WaitingRoomViewModel) {
        advanceTimeBy(STEP)
        runCurrent()
        assertTrue(vm.state.value.ui is WaitingRoomUiState.Active)
    }

    @Test
    fun startsInLoading() {
        assertEquals(WaitingRoomUiState.Loading, viewModel().state.value.ui)
    }

    @Test
    fun emitsEveryPhaseFromTheScriptedStub() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()
            val seen = mutableListOf<KClass<out WaitingRoomPhase>>()
            repeat(SCRIPT_STEPS) { index ->
                if (index > 0) step(vm)
                seen += vm.phase()::class
            }
            assertEquals(
                listOf(
                    WaitingRoomPhase.PreQueue::class,
                    WaitingRoomPhase.Waiting::class,
                    WaitingRoomPhase.Paused::class,
                    WaitingRoomPhase.YourTurn::class,
                    WaitingRoomPhase.MissedTurn::class,
                    WaitingRoomPhase.SoldOut::class,
                ),
                seen.distinct(),
            )
        }

    @Test
    fun positionNeverNegativeAcrossTheScript() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()
            repeat(SCRIPT_STEPS) { index ->
                if (index > 0) step(vm)
                when (val phase = vm.phase()) {
                    is WaitingRoomPhase.Waiting -> assertTrue(phase.queue.position >= 1)
                    is WaitingRoomPhase.Paused -> assertTrue(phase.position >= 0)
                    else -> Unit
                }
            }
        }

    @Test
    fun noPlaceBecomesEmpty() =
        runTest(dispatcher) {
            val vm =
                viewModel(
                    object : WaitingRoomRepository {
                        override fun observePlace(eventId: String): Flow<QueuePlace> = emptyFlow()
                    },
                )
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()
            assertEquals(WaitingRoomUiState.Empty, vm.state.value.ui)
        }

    @Test
    fun failureSetsLoadFailedAndScreenError() =
        runTest(dispatcher) {
            val vm =
                viewModel(
                    object : WaitingRoomRepository {
                        override fun observePlace(eventId: String): Flow<QueuePlace> = flow { error("boom") }
                    },
                )
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()
            assertTrue(vm.state.value.loadFailed)
            assertEquals(WaitingRoomUiState.Error("msg"), vm.state.value.toScreenState("msg"))
        }

    @Test
    fun backEmitsNavigateBack() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dispatch(WaitingRoomIntent.Back)
            runCurrent()
            assertEquals(WaitingRoomEffect.NavigateBack, vm.effect.first())
        }

    @Test
    fun chooseTicketsEmitsNavigateToDetail() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dispatch(WaitingRoomIntent.ChooseTicketsNow)
            runCurrent()
            assertEquals(WaitingRoomEffect.NavigateToDetail("evt"), vm.effect.first())
        }

    @Test
    fun browseOtherEventsEmitsNavigateToEvents() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dispatch(WaitingRoomIntent.BrowseOtherEvents)
            runCurrent()
            assertEquals(WaitingRoomEffect.NavigateToEvents, vm.effect.first())
        }

    @Test
    fun queueAgainRestartsObservation() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()
            step(vm)
            step(vm)
            assertIs<WaitingRoomPhase.Waiting>(vm.phase())
            vm.dispatch(WaitingRoomIntent.QueueAgain)
            runCurrent()
            assertIs<WaitingRoomPhase.PreQueue>(vm.phase())
            vm.clear()
        }

    @Test
    fun actionsMapToIntents() {
        assertEquals(WaitingRoomIntent.Back, WaitingRoomAction.Back.toIntent())
        assertEquals(WaitingRoomIntent.ChooseTicketsNow, WaitingRoomAction.EnterNow.toIntent())
        assertEquals(WaitingRoomIntent.BrowseOtherEvents, WaitingRoomAction.SeeOtherEvents.toIntent())
        assertEquals(WaitingRoomIntent.QueueAgain, WaitingRoomAction.QueueAgain.toIntent())
    }

    private companion object {
        const val STEP = 1_000L
        const val SCRIPT_STEPS = 10
    }
}
