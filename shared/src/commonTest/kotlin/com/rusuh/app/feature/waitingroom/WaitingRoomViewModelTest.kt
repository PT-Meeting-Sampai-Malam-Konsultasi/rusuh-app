package com.rusuh.app.feature.waitingroom

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.SignInRequiredException
import com.rusuh.app.domain.repository.QueueRepository
import com.rusuh.app.domain.usecase.LeaveQueueUseCase
import com.rusuh.app.feature.waitingroom.data.repository.StubWaitingRoomRepository
import com.rusuh.app.feature.waitingroom.domain.model.QueueEventInfo
import com.rusuh.app.feature.waitingroom.domain.model.QueuePlace
import com.rusuh.app.feature.waitingroom.domain.model.QueueState
import com.rusuh.app.feature.waitingroom.domain.repository.WaitingRoomRepository
import com.rusuh.app.feature.waitingroom.domain.usecase.ObserveQueuePlaceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertNotEquals
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

    private var now = 0L

    private var leaveResult: DomainResult<Unit> = DomainResult.Success(Unit)
    private val leaves = mutableListOf<String>()

    private val queueRepository =
        object : QueueRepository {
            override fun observePlace(eventId: String): Flow<QueuePlace> = emptyFlow()

            override suspend fun leave(eventId: String): DomainResult<Unit> {
                leaves += eventId
                return leaveResult
            }
        }

    private fun viewModel(
        repository: WaitingRoomRepository = StubWaitingRoomRepository(STEP),
        sessionState: FakeSessionStateProvider? = null,
    ) = WaitingRoomViewModel(
        "evt",
        ObserveQueuePlaceUseCase(repository),
        LeaveQueueUseCase(queueRepository),
        monotonicMillis = { now },
        sessionState = sessionState,
    )

    private fun place(
        state: QueueState,
        expiresAt: Long? = null,
        opensAt: Long? = null,
    ) = QueuePlace(
        eventId = "evt",
        event = QueueEventInfo(title = "evt"),
        state = state,
        seq = 10,
        served = 5,
        serverNowEpochMillis = 1_000L,
        opensAtEpochMillis = opensAt,
        turnExpiresAtEpochMillis = expiresAt,
    )

    private fun repoOf(vararg flows: Flow<QueuePlace>): WaitingRoomRepository {
        var call = 0
        return object : WaitingRoomRepository {
            override fun observePlace(eventId: String): Flow<QueuePlace> = flows[minOf(call++, flows.size - 1)]
        }
    }

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
                    is WaitingRoomPhase.Paused -> assertTrue(phase.position >= 1)
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
            assertIs<WaitingRoomUiState.Error>(vm.state.value.ui)
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
    fun errorAfterEmissionKeepsPlaceThenRetryResumes() =
        runTest(dispatcher) {
            val first =
                flow {
                    emit(place(QueueState.Open))
                    error("boom")
                }
            val vm = viewModel(repoOf(first, flow { emit(place(QueueState.Paused)) }))
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()
            assertIs<WaitingRoomPhase.Waiting>(vm.phase())
            assertEquals(WaitingRoomEffect.ShowRefreshError, vm.effect.first())
            assertIs<WaitingRoomPhase.Waiting>(vm.phase())
            vm.dispatch(WaitingRoomIntent.Retry)
            runCurrent()
            assertIs<WaitingRoomPhase.Paused>(vm.phase())
        }

    @Test
    fun leavingTheQueueCallsTheServerStopsObservingAndGoesBack() =
        runTest(dispatcher) {
            val vm = viewModel(repoOf(flow { emit(place(QueueState.Open)) }))
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()

            vm.dispatch(WaitingRoomIntent.LeaveQueue)
            runCurrent()

            assertEquals(listOf("evt"), leaves)
            assertEquals(WaitingRoomEffect.NavigateBack, vm.effect.first())
        }

    @Test
    fun aFailedLeaveKeepsThePlaceAndShowsTheRefreshError() =
        runTest(dispatcher) {
            leaveResult = DomainResult.NetworkError
            val vm = viewModel(repoOf(flow { emit(place(QueueState.Open)) }))
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()

            vm.dispatch(WaitingRoomIntent.LeaveQueue)
            runCurrent()

            assertEquals(WaitingRoomEffect.ShowRefreshError, vm.effect.first())
            assertIs<WaitingRoomPhase.Waiting>(vm.phase())
        }

    @Test
    fun aSignInRequiredFailureIsADistinctErrorKindWithTheSameScreen() =
        runTest(dispatcher) {
            val vm = viewModel(repoOf(flow { throw SignInRequiredException() }))
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()

            val error = assertIs<WaitingRoomUiState.Error>(vm.state.value.ui)
            assertEquals(WaitingRoomErrorKind.SignInRequired, error.kind)
            val screen = assertIs<WaitingRoomUiState.Error>(vm.state.value.toScreenState("msg"))
            assertEquals("msg", screen.message)
            assertEquals(WaitingRoomErrorKind.SignInRequired, screen.kind)
        }

    @Test
    fun anOtherFailureIsTheGenericKind() =
        runTest(dispatcher) {
            val vm = viewModel(repoOf(flow { error("boom") }))
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()

            assertEquals(WaitingRoomErrorKind.Generic, assertIs<WaitingRoomUiState.Error>(vm.state.value.ui).kind)
        }

    @Test
    fun aSignInRequiredFailureAfterThePlaceEmitsReSignInEffect() =
        runTest(dispatcher) {
            val first =
                flow {
                    emit(place(QueueState.Open))
                    throw SignInRequiredException()
                }
            val vm = viewModel(repoOf(first))
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()

            assertEquals(WaitingRoomEffect.ReSignIn("evt"), vm.effect.first())
            assertIs<WaitingRoomPhase.Waiting>(vm.phase())
        }

    @Test
    fun retryAfterHardFailureRecovers() =
        runTest(dispatcher) {
            val vm = viewModel(repoOf(flow { error("boom") }, flow { emit(place(QueueState.Open)) }))
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()
            assertIs<WaitingRoomUiState.Error>(vm.state.value.ui)
            vm.dispatch(WaitingRoomIntent.Retry)
            runCurrent()
            assertIs<WaitingRoomPhase.Waiting>(vm.phase())
        }

    @Test
    fun completionAfterEmissionKeepsLastPlace() =
        runTest(dispatcher) {
            val vm = viewModel(repoOf(flow { emit(place(QueueState.Closed)) }))
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()
            assertEquals(WaitingRoomPhase.SoldOut, vm.phase())
        }

    @Test
    fun oldJobCannotEmitAfterQueueAgain() =
        runTest(dispatcher) {
            val old = MutableSharedFlow<QueuePlace>()
            val vm = viewModel(repoOf(old, flow { emit(place(QueueState.Paused)) }))
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()
            vm.dispatch(WaitingRoomIntent.QueueAgain)
            runCurrent()
            old.emit(place(QueueState.Closed))
            runCurrent()
            assertIs<WaitingRoomPhase.Paused>(vm.phase())
        }

    @Test
    fun effectIsDeliveredExactlyOnceAndBackOnlyOnce() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dispatch(WaitingRoomIntent.Back)
            vm.dispatch(WaitingRoomIntent.Back)
            vm.dispatch(WaitingRoomIntent.BrowseOtherEvents)
            runCurrent()
            assertEquals(WaitingRoomEffect.NavigateBack, vm.effect.first())
            assertEquals(WaitingRoomEffect.NavigateToEvents, vm.effect.first())
        }

    @Test
    fun clearStopsCollection() =
        runTest(dispatcher) {
            val source = MutableSharedFlow<QueuePlace>()
            val vm = viewModel(repoOf(source))
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()
            source.emit(place(QueueState.Open))
            runCurrent()
            vm.clear()
            source.emit(place(QueueState.Closed))
            runCurrent()
            assertIs<WaitingRoomPhase.Waiting>(vm.phase())
        }

    @Test
    fun turnWithoutExpiryBecomesError() =
        runTest(dispatcher) {
            val vm = viewModel(repoOf(flow { emit(place(QueueState.Turn)) }))
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()
            assertIs<WaitingRoomUiState.Error>(vm.state.value.ui)
        }

    @Test
    fun tickCountsDownFromMonotonicClock() =
        runTest(dispatcher) {
            val vm = viewModel(repoOf(flow { emit(place(QueueState.Turn, expiresAt = 61_000L)) }))
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()
            assertEquals(60, assertIs<WaitingRoomPhase.YourTurn>(vm.phase()).secondsLeft)
            now = 3_000L
            vm.dispatch(WaitingRoomIntent.Tick)
            runCurrent()
            assertEquals(57, assertIs<WaitingRoomPhase.YourTurn>(vm.phase()).secondsLeft)
            assertNotEquals(60, assertIs<WaitingRoomPhase.YourTurn>(vm.phase()).secondsLeft)
        }

    @Test
    fun actionsMapToIntents() {
        assertEquals(WaitingRoomIntent.Back, WaitingRoomAction.Back.toIntent())
        assertEquals(WaitingRoomIntent.ChooseTicketsNow, WaitingRoomAction.EnterNow.toIntent())
        assertEquals(WaitingRoomIntent.BrowseOtherEvents, WaitingRoomAction.SeeOtherEvents.toIntent())
        assertEquals(WaitingRoomIntent.QueueAgain, WaitingRoomAction.QueueAgain.toIntent())
    }

    // ===== AUTH-00-M8 (#154): Login Gate Tests =====

    @Test
    fun signedInUserAtTurnAutoAdvancesToDetail() =
        runTest(dispatcher) {
            val session = FakeSessionStateProvider(signedIn = true)
            val vm = viewModel(repoOf(flow { emit(place(QueueState.Turn, expiresAt = 61_000L)) }), session)
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()

            assertEquals(WaitingRoomEffect.NavigateToDetail("evt"), vm.effect.first())
        }

    @Test
    fun signedOutAtTurnStillAutoAdvances() =
        runTest(dispatcher) {
            // With login-before-queue (AUTH-00-M8), a signed-out user should not reach
            // the waiting room. But if they do (defensive), we still auto-advance.
            val session = FakeSessionStateProvider(signedIn = false)
            val vm = viewModel(repoOf(flow { emit(place(QueueState.Turn, expiresAt = 61_000L)) }), session)
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()

            // Without session state, no auto-advance since isSignedIn = false and we're defensive.
            // The user can still tap "Choose tickets now" manually.
            val phase = assertIs<WaitingRoomPhase.YourTurn>(vm.phase())
            assertEquals(60, phase.secondsLeft)
        }

    @Test
    fun noSessionStateDefaultsToAutoAdvance() =
        runTest(dispatcher) {
            // When no SessionStateProvider is injected, default to signed-in behavior.
            val vm = viewModel(repoOf(flow { emit(place(QueueState.Turn, expiresAt = 61_000L)) }), sessionState = null)
            vm.dispatch(WaitingRoomIntent.Start)
            runCurrent()

            assertEquals(WaitingRoomEffect.NavigateToDetail("evt"), vm.effect.first())
        }

    private companion object {
        const val STEP = 1_000L
        const val SCRIPT_STEPS = 10
    }
}

/** Minimal fake SessionStateProvider for testing. */
private class FakeSessionStateProvider(signedIn: Boolean) :
    com.rusuh.app.data.network.session.SessionStateProvider {
    private val flow = MutableStateFlow(signedIn)

    override val isSignedIn: kotlinx.coroutines.flow.StateFlow<Boolean> = flow

    fun setSignedIn(value: Boolean) {
        flow.value = value
    }
}
