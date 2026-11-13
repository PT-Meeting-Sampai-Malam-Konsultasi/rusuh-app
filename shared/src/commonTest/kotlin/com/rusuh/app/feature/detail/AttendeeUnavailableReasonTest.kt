package com.rusuh.app.feature.detail

import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.AccountProfile
import com.rusuh.app.domain.model.AttendeeValidation
import com.rusuh.app.domain.model.Booking
import com.rusuh.app.domain.model.BookingRequest
import com.rusuh.app.domain.model.BookingResult
import com.rusuh.app.domain.model.DetailOffering
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import com.rusuh.app.domain.model.EventDetail
import com.rusuh.app.domain.model.SeatCategory
import com.rusuh.app.domain.repository.DetailRepository
import com.rusuh.app.domain.usecase.CreateBookingUseCase
import com.rusuh.app.domain.usecase.GetDetailUseCase
import com.rusuh.app.domain.usecase.ValidateAttendeeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AttendeeUnavailableReasonTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Repository(private val outcome: AttendeeValidation) : DetailRepository {
        override suspend fun getDetail(eventId: String) =
            DetailOffering(
                event = EventDetail("e1", "Event", null, "meta"),
                categories = listOf(SeatCategory("a", "A", null, 850_000, stock = 100)),
                profile = AccountProfile("Nadia Saputri", "n@x.id"),
                adminFeeRupiah = 10_000,
                reservedUnits = 3,
                expiresAtMillis = 300_000L,
            )

        override suspend fun validateAttendee(
            eventId: String,
            fieldIndex: Int,
            fullName: String,
            nik: String,
        ) = outcome

        @Suppress("DEPRECATION")
        @Deprecated("Use validateAttendee instead", ReplaceWith("validateAttendee(...)"))
        override suspend fun isNikAccepted(nik: String) = true

        override suspend fun createBooking(
            request: BookingRequest,
            idempotencyKey: String,
        ): BookingResult = BookingResult.Success(Booking("b-1"))

        override suspend fun getBooking(bookingId: String): DomainResult<Booking> =
            DomainResult.Success(
                Booking(bookingId),
            )

        override suspend fun cancelBooking(bookingId: String): DomainResult<Booking> =
            DomainResult.Success(Booking(bookingId))

        override suspend fun getActiveBookings(): DomainResult<List<Booking>> = DomainResult.Success(emptyList())

        @Suppress("DEPRECATION")
        @Deprecated("Use createBooking with idempotencyKey instead")
        override suspend fun createBooking(request: BookingRequest): Booking = Booking("b-1")
    }

    private fun TestScope.reasonFor(outcome: AttendeeValidation): UnavailableReason {
        val repository = Repository(outcome)
        val vm =
            DetailViewModel(
                eventId = "e1",
                getDetail = GetDetailUseCase(repository),
                validateAttendee = ValidateAttendeeUseCase(repository),
                createBooking = CreateBookingUseCase(repository),
                serverClock =
                    object : ServerClock {
                        override fun nowMillis() = 0L
                    },
            )
        vm.dispatch(DetailIntent.Load)
        testScheduler.advanceUntilIdle()
        vm.dispatch(DetailIntent.IncrementCategory("a"))
        vm.dispatch(DetailIntent.AttendeeNameChanged(0, "Name"))
        vm.dispatch(DetailIntent.AttendeeNikChanged(0, "3174055203960004"))
        testScheduler.advanceUntilIdle()
        advanceTimeBy(NIK_VALIDATION_DEBOUNCE_MS + 100)
        testScheduler.advanceUntilIdle()
        val status = vm.state.value.form.attendees.first().validationStatus
        assertIs<AttendeeValidationStatus.Unavailable>(status)
        return status.reason
    }

    @Test
    fun noConnectionShowsTheNetworkReason() =
        runTest(dispatcher) { assertEquals(UnavailableReason.NETWORK, reasonFor(AttendeeValidation.NetworkError)) }

    @Test
    fun idCheckServiceDownShowsTheServiceReason() =
        runTest(dispatcher) {
            assertEquals(UnavailableReason.SERVICE, reasonFor(AttendeeValidation.DukcapilUnavailable))
        }

    @Test
    fun rateLimitShowsTheTooManyAttemptsReason() =
        runTest(dispatcher) {
            assertEquals(UnavailableReason.TOO_MANY_ATTEMPTS, reasonFor(AttendeeValidation.RateLimited(30)))
        }

    @Test
    fun otherServerErrorsShowTheGenericReason() =
        runTest(dispatcher) {
            val outcome = AttendeeValidation.ServerError(500, ErrorCode.UNKNOWN)
            assertEquals(UnavailableReason.SERVER, reasonFor(outcome))
        }
}
