package com.rusuh.app.feature.detail

import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.AccountProfile
import com.rusuh.app.domain.model.AttendeeValidation
import com.rusuh.app.domain.model.Booking
import com.rusuh.app.domain.model.BookingRequest
import com.rusuh.app.domain.model.DetailOffering
import com.rusuh.app.domain.model.EventDetail
import com.rusuh.app.domain.model.SeatCategory
import com.rusuh.app.domain.model.validateAttendeeLocally
import com.rusuh.app.domain.repository.DetailRepository
import com.rusuh.app.domain.usecase.CreateBookingUseCase
import com.rusuh.app.domain.usecase.GetDetailUseCase
import com.rusuh.app.domain.usecase.ValidateAttendeeUseCase
import com.rusuh.app.ui.components.SeatAvailability
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val nikA = "3174055203960004"
    private val nikB = "3201123456780001"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeClock(var now: Long = 0L) : ServerClock {
        override fun nowMillis(): Long = now
    }

    private class FakeRepository(
        private val offering: DetailOffering,
        private val loadError: Throwable? = null,
        private val nikAccepted: Boolean = true,
        private val bookingError: Throwable? = null,
    ) : DetailRepository {
        var bookings = mutableListOf<BookingRequest>()

        override suspend fun getDetail(eventId: String): DetailOffering {
            loadError?.let { throw it }
            return offering
        }

        override suspend fun isNikAccepted(nik: String): Boolean = nikAccepted

        override suspend fun createBooking(request: BookingRequest): Booking {
            bookingError?.let { throw it }
            bookings += request
            return Booking("b-1")
        }
    }

    private fun offering(
        expiresAt: Long = 300_000L,
        categories: List<SeatCategory> =
            listOf(
                SeatCategory("sold", "Sold", null, 1_000_000, stock = 0),
                SeatCategory("low", "Low", null, 650_000, stock = 2),
                SeatCategory("a", "A", null, 850_000, stock = 100),
                SeatCategory("b", "B", null, 450_000, stock = 100),
            ),
        reserved: Int = 3,
    ) = DetailOffering(
        event = EventDetail("e1", "Event", null, "meta"),
        categories = categories,
        profile = AccountProfile("Nadia Saputri", "n@x.id"),
        adminFeeRupiah = 10_000,
        reservedUnits = reserved,
        expiresAtMillis = expiresAt,
    )

    private fun TestScope.viewModel(
        repository: FakeRepository = FakeRepository(offering()),
        clock: FakeClock = FakeClock(),
    ): DetailViewModel {
        val vm =
            DetailViewModel(
                eventId = "e1",
                getDetail = GetDetailUseCase(repository),
                validateAttendee = ValidateAttendeeUseCase(repository),
                createBooking = CreateBookingUseCase(repository),
                serverClock = clock,
            )
        vm.dispatch(DetailIntent.Load)
        testScheduler.advanceUntilIdle()
        return vm
    }

    private fun TestScope.send(
        vm: DetailViewModel,
        vararg intents: DetailIntent,
    ) {
        intents.forEach { vm.dispatch(it) }
        testScheduler.advanceUntilIdle()
    }

    private fun TestScope.fillAttendees(
        vm: DetailViewModel,
        vararg niks: String,
    ) {
        niks.forEachIndexed { i, nik ->
            send(vm, DetailIntent.AttendeeNameChanged(i, "Name $i"), DetailIntent.AttendeeNikChanged(i, nik))
        }
    }

    @Test
    fun loadMapsOfferingToReadyStateWithAvailability() =
        runTest(dispatcher) {
            val vm = viewModel()
            val state = vm.state.value
            assertEquals(DetailPhase.Ready, state.phase)
            assertEquals(300, state.remainingSeconds)
            val availability = state.detail!!.categories.associate { it.id to it.availability }
            assertEquals(SeatAvailability.SoldOut, availability["sold"])
            assertEquals(SeatAvailability.AlmostGone, availability["low"])
            assertEquals(SeatAvailability.Available, availability["a"])
        }

    @Test
    fun loadFailureShowsErrorStateAndEffect() =
        runTest(dispatcher) {
            val vm = viewModel(FakeRepository(offering(), loadError = IllegalStateException("boom")))
            assertEquals(DetailPhase.Failed(DetailError.LoadFailed), vm.state.value.phase)
            assertEquals(DetailEffect.ShowError(DetailError.LoadFailed), vm.effect.first())
        }

    @Test
    fun emptyCategoriesShowEmptyState() =
        runTest(dispatcher) {
            val vm = viewModel(FakeRepository(offering(categories = emptyList())))
            assertEquals(DetailPhase.Empty, vm.state.value.phase)
        }

    @Test
    fun incrementIsCappedAtThreeTicketsAcrossCategories() =
        runTest(dispatcher) {
            val vm = viewModel()
            send(
                vm,
                DetailIntent.IncrementCategory("a"),
                DetailIntent.IncrementCategory("a"),
                DetailIntent.IncrementCategory("b"),
                DetailIntent.IncrementCategory("b"),
                DetailIntent.IncrementCategory("a"),
            )
            val form = vm.state.value.form
            assertEquals(3, form.totalTickets)
            assertEquals(2, form.quantities["a"])
            assertEquals(1, form.quantities["b"])
            assertEquals(3, form.attendees.size)
        }

    @Test
    fun capFollowsReservedUnitsWhenBelowThree() =
        runTest(dispatcher) {
            val vm = viewModel(FakeRepository(offering(reserved = 2)))
            send(vm, *Array(3) { DetailIntent.IncrementCategory("a") })
            assertEquals(2, vm.state.value.form.totalTickets)
        }

    @Test
    fun soldOutCategoryCannotBeIncremented() =
        runTest(dispatcher) {
            val vm = viewModel()
            send(vm, DetailIntent.IncrementCategory("sold"))
            assertEquals(0, vm.state.value.form.totalTickets)
        }

    @Test
    fun quantityIsBoundedByCategoryStock() =
        runTest(dispatcher) {
            val vm = viewModel()
            send(vm, *Array(3) { DetailIntent.IncrementCategory("low") })
            assertEquals(2, vm.state.value.form.quantities["low"])
        }

    @Test
    fun decrementNeverGoesBelowZeroAndShrinksAttendees() =
        runTest(dispatcher) {
            val vm = viewModel()
            send(vm, DetailIntent.DecrementCategory("a"))
            assertEquals(0, vm.state.value.form.totalTickets)
            send(
                vm,
                DetailIntent.IncrementCategory("a"),
                DetailIntent.IncrementCategory("a"),
                DetailIntent.DecrementCategory("a"),
            )
            assertEquals(1, vm.state.value.form.quantities["a"])
            assertEquals(1, vm.state.value.form.attendees.size)
        }

    @Test
    fun selfModePrefillsNameOnlyAndNeverNik() =
        runTest(dispatcher) {
            val vm = viewModel()
            send(vm, DetailIntent.IncrementCategory("a"), DetailIntent.AttendeeKindChanged(0, AttendeeKind.Self))
            val attendee = vm.state.value.form.attendees.single()
            assertEquals("Nadia Saputri", attendee.name)
            assertEquals("", attendee.nik)
            assertFalse(vm.state.value.canProceed)
        }

    @Test
    fun nikEditKeepsDigitsOnlyUpToSixteen() =
        runTest(dispatcher) {
            val vm = viewModel()
            send(vm, DetailIntent.IncrementCategory("a"), DetailIntent.AttendeeNikChanged(0, "31-74a0552 03960004999"))
            assertEquals(nikA, vm.state.value.form.attendees.single().nik)
        }

    @Test
    fun proceedEnabledOnlyWhenEveryAttendeeIsValidAndDistinct() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertFalse(vm.state.value.canProceed, "no tickets")
            send(vm, DetailIntent.IncrementCategory("a"), DetailIntent.IncrementCategory("b"))
            fillAttendees(vm, nikA, nikA)
            assertFalse(vm.state.value.canProceed, "duplicate NIK")
            send(vm, DetailIntent.AttendeeNikChanged(1, nikB))
            assertTrue(vm.state.value.canProceed)
            send(vm, DetailIntent.AttendeeNikChanged(1, "123"))
            assertFalse(vm.state.value.canProceed, "short NIK")
            send(vm, DetailIntent.AttendeeNikChanged(1, nikB), DetailIntent.AttendeeNameChanged(1, " "))
            assertFalse(vm.state.value.canProceed, "blank name")
        }

    @Test
    fun totalIsSubtotalPlusOneServiceFeePerOrder() =
        runTest(dispatcher) {
            val vm = viewModel()
            send(
                vm,
                DetailIntent.IncrementCategory("a"),
                DetailIntent.IncrementCategory("a"),
                DetailIntent.IncrementCategory("b"),
            )
            assertEquals(2 * 850_000 + 450_000, vm.state.value.subtotalRupiah)
            assertEquals(2 * 850_000 + 450_000 + 10_000, vm.state.value.totalRupiah)
        }

    @Test
    fun proceedCreatesBookingAndEmitsNavigateToPayment() =
        runTest(dispatcher) {
            val repository = FakeRepository(offering())
            val vm = viewModel(repository)
            send(vm, DetailIntent.IncrementCategory("a"))
            fillAttendees(vm, nikA)
            send(vm, DetailIntent.Proceed)
            assertEquals(DetailEffect.NavigateToPayment("b-1"), vm.effect.first())
            val ticket = repository.bookings.single().tickets.single()
            assertEquals("a", ticket.categoryId)
            assertEquals(nikA, ticket.holderNik)
            assertFalse(vm.state.value.isSubmitting)
        }

    @Test
    fun proceedIsIgnoredWhenInvalid() =
        runTest(dispatcher) {
            val repository = FakeRepository(offering())
            val vm = viewModel(repository)
            send(vm, DetailIntent.IncrementCategory("a"), DetailIntent.Proceed)
            assertTrue(repository.bookings.isEmpty())
        }

    @Test
    fun serverNikRejectionShowsErrorAndDoesNotBook() =
        runTest(dispatcher) {
            val repository = FakeRepository(offering(), nikAccepted = false)
            val vm = viewModel(repository)
            send(vm, DetailIntent.IncrementCategory("a"))
            fillAttendees(vm, nikA)
            send(vm, DetailIntent.Proceed)
            assertEquals(DetailEffect.ShowError(DetailError.NikRejected), vm.effect.first())
            assertTrue(repository.bookings.isEmpty())
            assertFalse(vm.state.value.isSubmitting)
        }

    @Test
    fun bookingFailureShowsErrorAndKeepsForm() =
        runTest(dispatcher) {
            val vm = viewModel(FakeRepository(offering(), bookingError = IllegalStateException("x")))
            send(vm, DetailIntent.IncrementCategory("a"))
            fillAttendees(vm, nikA)
            send(vm, DetailIntent.Proceed)
            assertEquals(DetailEffect.ShowError(DetailError.BookingFailed), vm.effect.first())
            assertEquals(1, vm.state.value.form.totalTickets)
        }

    @Test
    fun navigationIntentsEmitEffects() =
        runTest(dispatcher) {
            val vm = viewModel()
            send(vm, DetailIntent.Back)
            assertEquals(DetailEffect.NavigateBack, vm.effect.first())
            send(vm, DetailIntent.GoHome)
            assertEquals(DetailEffect.NavigateHome, vm.effect.first())
            send(vm, DetailIntent.QueueAgain)
            assertEquals(DetailEffect.NavigateToQueue("e1"), vm.effect.first())
        }

    @Test
    fun tickRecomputesRemainingFromTheServerClock() =
        runTest(dispatcher) {
            val clock = FakeClock()
            val vm = viewModel(clock = clock)
            clock.now = 48_000L
            send(vm, DetailIntent.Tick)
            assertEquals(252, vm.state.value.remainingSeconds)
            assertEquals("4:12", formatCountdown(vm.state.value.remainingSeconds))
        }

    @Test
    fun warningThresholdIsReachedNearTheEnd() =
        runTest(dispatcher) {
            val clock = FakeClock()
            val vm = viewModel(clock = clock)
            clock.now = 300_000L - 60_000L
            send(vm, DetailIntent.Tick)
            assertTrue(vm.state.value.remainingSeconds <= COUNTDOWN_WARN_SECONDS)
            clock.now = 100_000L
            send(vm, DetailIntent.Tick)
            assertTrue(vm.state.value.remainingSeconds > COUNTDOWN_WARN_SECONDS)
        }

    @Test
    fun reachingZeroExpiresAndClearsAttendeeData() =
        runTest(dispatcher) {
            val clock = FakeClock()
            val vm = viewModel(clock = clock)
            send(vm, DetailIntent.IncrementCategory("a"))
            fillAttendees(vm, nikA)
            clock.now = 300_000L
            send(vm, DetailIntent.Tick)
            val state = vm.state.value
            assertEquals(DetailPhase.Expired, state.phase)
            assertEquals(0, state.remainingSeconds)
            assertEquals(DetailFormState(), state.form)
            assertIs<DetailUiState.Expired>(state.toUiState { "" })
            // Edits and proceed are inert once expired.
            send(vm, DetailIntent.IncrementCategory("a"), DetailIntent.Proceed)
            assertEquals(0, vm.state.value.form.totalTickets)
        }

    @Test
    fun clockPastExpiryDuringProceedExpiresInsteadOfBooking() =
        runTest(dispatcher) {
            val clock = FakeClock()
            val repository = FakeRepository(offering())
            val vm = viewModel(repository, clock)
            send(vm, DetailIntent.IncrementCategory("a"))
            fillAttendees(vm, nikA)
            clock.now = 400_000L
            send(vm, DetailIntent.Proceed)
            assertEquals(DetailPhase.Expired, vm.state.value.phase)
            assertTrue(repository.bookings.isEmpty())
        }

    @Test
    fun loadAfterExpiryWindowStartsExpired() =
        runTest(dispatcher) {
            val vm = viewModel(FakeRepository(offering(expiresAt = 1_000L)), FakeClock(now = 5_000L))
            assertEquals(DetailPhase.Expired, vm.state.value.phase)
        }

    @Test
    fun remainingSecondsRoundsUpAndNeverGoesNegative() {
        assertEquals(300, remainingSeconds(300_000L, 0L))
        assertEquals(1, remainingSeconds(1_000L, 1L))
        assertEquals(0, remainingSeconds(1_000L, 1_000L))
        assertEquals(0, remainingSeconds(1_000L, 9_000L))
    }

    @Test
    fun uiStateMappingCoversEveryPhase() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertIs<DetailUiState.Content>(vm.state.value.toUiState { "" })
            assertIs<DetailUiState.Loading>(DetailState().toUiState { "" })
            val failed = DetailState(phase = DetailPhase.Failed(DetailError.LoadFailed)).toUiState { "msg" }
            assertEquals("msg", (failed as DetailUiState.Error).message)
        }

    @Test
    fun localAttendeeValidationRules() {
        assertEquals(AttendeeValidation.MissingName, validateAttendeeLocally("", nikA, emptyList()))
        assertEquals(AttendeeValidation.InvalidNik, validateAttendeeLocally("N", "123", emptyList()))
        assertEquals(AttendeeValidation.InvalidNik, validateAttendeeLocally("N", nikA.dropLast(1) + "x", emptyList()))
        assertEquals(AttendeeValidation.DuplicateNik(2), validateAttendeeLocally("N", nikA, listOf(nikB, nikA)))
        assertEquals(AttendeeValidation.Valid, validateAttendeeLocally("N", nikA, listOf(nikB)))
        assertNull(null)
    }
}
