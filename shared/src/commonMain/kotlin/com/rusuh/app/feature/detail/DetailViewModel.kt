package com.rusuh.app.feature.detail

import com.rusuh.app.data.repository.newIdempotencyKey
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.AttendeeValidation
import com.rusuh.app.domain.model.BookingResult
import com.rusuh.app.domain.model.isValid
import com.rusuh.app.domain.usecase.CreateBookingUseCase
import com.rusuh.app.domain.usecase.GetDetailUseCase
import com.rusuh.app.domain.usecase.ValidateAttendeeUseCase
import com.rusuh.app.mvi.MviViewModel
import kotlin.coroutines.cancellation.CancellationException

/**
 * MVI ViewModel for the Detail Ticket page.
 *
 * Talks to the data layer through use cases only. The countdown is recomputed on every
 * [DetailIntent.Tick] as `expiresAt - serverClock.now` (never the device clock); the host decides
 * how often to tick.
 *
 * @param eventId the event this order is for.
 * @param serverClock server-time source (fake in tests). TODO(FR-17-M1 #82): synced clock.
 */
@Suppress("TooManyFunctions")
class DetailViewModel(
    private val eventId: String,
    private val getDetail: GetDetailUseCase,
    private val validateAttendee: ValidateAttendeeUseCase,
    private val createBooking: CreateBookingUseCase,
    private val serverClock: ServerClock,
) : MviViewModel<DetailState, DetailIntent, DetailEffect>(DetailState()) {
    @Suppress("CyclomaticComplexMethod")
    override suspend fun reduce(intent: DetailIntent) {
        when (intent) {
            // Load once per ViewModel: re-entering the route must not reset the form or the window.
            DetailIntent.Load -> if (!loadStarted) load()
            DetailIntent.Retry -> if (currentState.phase is DetailPhase.Failed) load()
            DetailIntent.Back -> sendEffect(DetailEffect.NavigateBack)
            DetailIntent.GoHome -> sendEffect(DetailEffect.NavigateHome)
            DetailIntent.QueueAgain -> sendEffect(DetailEffect.NavigateToQueue(eventId))
            // TODO(FR-APP account settings): open the email change flow.
            DetailIntent.ChangeEmail -> Unit
            DetailIntent.Tick -> tick()
            DetailIntent.Proceed -> proceed()
            is DetailIntent.IncrementCategory -> changeQuantity(intent.categoryId, +1)
            is DetailIntent.DecrementCategory -> changeQuantity(intent.categoryId, -1)
            is DetailIntent.AttendeeNameChanged -> editForm { _, form -> form.withName(intent.index, intent.name) }
            is DetailIntent.AttendeeNikChanged -> editForm { _, form -> form.withNik(intent.index, intent.nik) }
        }
    }

    private var loadStarted = false

    /** Idempotency key for the current booking attempt; regenerated on each new attempt. */
    private var idempotencyKey: String = newIdempotencyKey()

    @Suppress("SwallowedException")
    private suspend fun load() {
        loadStarted = true
        setState { copy(phase = DetailPhase.Loading) }
        try {
            val offering = getDetail(eventId)
            val content = offering.toDetailContent()
            val remaining = remainingSeconds(offering.expiresAtMillis, serverClock.nowMillis())
            setState {
                copy(
                    phase =
                        when {
                            // No categories, or the queue reserved no units: nothing can be bought.
                            content.categories.isEmpty() || content.maxTickets <= 0 -> DetailPhase.Empty
                            remaining <= 0 -> DetailPhase.Expired
                            else -> DetailPhase.Ready
                        },
                    detail = content,
                    form = DetailFormState(),
                    expiresAtMillis = offering.expiresAtMillis,
                    remainingSeconds = remaining,
                    isSubmitting = false,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            // Intentionally broad: any repository/network failure maps to the error state.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // The error screen is the single signal for a failed load (no extra snackbar).
            setState { copy(phase = DetailPhase.Failed(DetailError.LoadFailed)) }
        }
    }

    private fun tick() {
        if (currentState.phase != DetailPhase.Ready) return
        val remaining = remainingSeconds(currentState.expiresAtMillis, serverClock.nowMillis())
        if (remaining <= 0) expire() else setState { copy(remainingSeconds = remaining) }
    }

    /** Window elapsed: drop the selection, attendee data (PII), and validated tokens, then show the expired sheet. */
    private fun expire() {
        // Clear validated attendees to prevent stale tokens from being reused if the user queues again.
        validatedAttendees = mutableListOf()
        setState {
            copy(
                phase = DetailPhase.Expired,
                remainingSeconds = 0,
                form = DetailFormState(),
                isSubmitting = false,
            )
        }
    }

    private fun changeQuantity(
        categoryId: String,
        delta: Int,
    ) {
        editForm { content, form ->
            form.withQuantity(
                categoryId = categoryId,
                quantity = (form.quantities[categoryId] ?: 0) + delta,
                maxTickets = content.maxTickets,
                stock = content.stockOf(categoryId),
            )
        }
    }

    /** Applies [edit] to the form; ignored unless the page is ready and not submitting. */
    private fun editForm(edit: (DetailContent, DetailFormState) -> DetailFormState) {
        val state = currentState
        val content = state.detail
        if (state.phase != DetailPhase.Ready || content == null || state.isSubmitting) return
        setState { copy(form = edit(content, form)) }
    }

    @Suppress("SwallowedException", "LongMethod", "CyclomaticComplexMethod")
    private suspend fun proceed() {
        if (!canStartProceed()) return
        setState { copy(isSubmitting = true) }
        try {
            executeProceedFlow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            // Intentionally broad: any repository/network failure maps to a transient error.
            // Keep the same idempotency key for transient network failures so automatic retries
            // with the same key are safe (the server de-duplicates them if the first succeeded).
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            if (isOrderActive()) {
                // DO NOT regenerate idempotency key for network errors.
                // The request may have succeeded server-side, and a retry with the same key
                // will correctly detect the duplicate instead of creating a second booking.
                sendEffect(DetailEffect.ShowError(DetailError.ServiceUnavailable))
            }
        } finally {
            setState { copy(isSubmitting = false) }
        }
    }

    /** Checks preconditions for proceed; expires if time ran out. */
    private fun canStartProceed(): Boolean {
        val state = currentState
        val content = state.detail
        val ready = state.phase == DetailPhase.Ready && content != null && state.canProceed
        if (!ready) return false
        val hasTime = remainingSeconds(state.expiresAtMillis, serverClock.nowMillis()) > 0
        if (!hasTime) expire()
        return hasTime
    }

    /** Core proceed flow: validate attendees then create booking. */
    private suspend fun executeProceedFlow() {
        val form = currentState.form
        // Validate each attendee with the server and collect validation tokens.
        val validationResult = validateAllAttendees(form)
        if (validationResult != null) {
            handleValidationError(validationResult)
            return
        }
        // Re-check time between validations and booking, then create booking.
        if (isOrderActive()) {
            createBookingAndHandleResult(form)
        }
    }

    /**
     * Validates all attendees in the form sequentially.
     * Returns null if all validations pass, or the first failed validation.
     */
    private suspend fun validateAllAttendees(form: DetailFormState): AttendeeValidation? {
        for (i in form.attendees.indices) {
            val a = form.attendees[i]
            val earlierNiks = form.attendees.take(i).map { it.nik }
            val validation = validateAttendee(eventId, i, a.name, a.nik, earlierNiks)
            if (!validation.isValid) {
                return validation
            }
            val token = (validation as AttendeeValidation.Valid).validationToken
            validatedAttendees += ValidatedAttendee(a.categoryId, a.name.trim(), a.nik, token)
        }
        return null
    }

    /** Collected validation tokens for the current booking attempt. */
    private var validatedAttendees = mutableListOf<ValidatedAttendee>()

    /** Creates a booking and handles the result. */
    private suspend fun createBookingAndHandleResult(form: DetailFormState) {
        val bookingRequest = form.toBookingRequestWithTokens(eventId, validatedAttendees)
        validatedAttendees = mutableListOf() // Reset for next attempt.
        val result = createBooking(bookingRequest, idempotencyKey)

        if (!isOrderActive()) {
            // Window closed during the booking call. The booking may have succeeded server-side.
            // TODO(FR-16-M1 #81): cancel the booking if it was created.
            return
        }

        handleBookingResult(result)
    }

    private fun handleBookingResult(result: BookingResult) {
        when (result) {
            is BookingResult.Success -> {
                sendEffect(DetailEffect.NavigateToPayment(result.booking.bookingId))
            }
            is BookingResult.AccountHasActiveBooking -> {
                // User has an active booking; offer resume via getActiveBookings (UI follow-up).
                // Regenerate key: user must start a new attempt after resolving the active booking.
                idempotencyKey = newIdempotencyKey()
                sendEffect(DetailEffect.ShowError(DetailError.BookingFailed))
            }
            is BookingResult.CategorySoldOut -> {
                // User must pick a different category, so a new attempt.
                idempotencyKey = newIdempotencyKey()
                sendEffect(DetailEffect.ShowError(DetailError.BookingFailed))
            }
            is BookingResult.AdmissionInvalid,
            is BookingResult.SessionExpired,
            -> {
                // Back to the waiting room; no retry is possible, so regenerate for next time.
                idempotencyKey = newIdempotencyKey()
                sendEffect(DetailEffect.NavigateToQueue(eventId))
            }
            // Transient errors where the SAME idempotency key should be used for retry:
            // - NetworkError: the request may not have reached the server
            // - ServiceUnavailable: server is temporarily overloaded
            // - RateLimited: can retry after the cooldown with the same key
            is BookingResult.NetworkError,
            is BookingResult.ServiceUnavailable,
            is BookingResult.RateLimited,
            -> {
                // Keep the same idempotency key for transient errors so retry is safe.
                // If the original request succeeded server-side, the duplicate is detected.
                sendEffect(DetailEffect.ShowError(DetailError.ServiceUnavailable))
            }
            // Non-retryable errors that require user action (changing the form):
            is BookingResult.QuantityOverReservation,
            is BookingResult.AttendeeNotValidated,
            is BookingResult.NikSeatTaken,
            is BookingResult.DuplicateNik,
            is BookingResult.BookingNotHeld,
            is BookingResult.ServerError,
            -> {
                // Regenerate idempotency key: user must fix the issue and it's a new attempt.
                idempotencyKey = newIdempotencyKey()
                sendEffect(DetailEffect.ShowError(DetailError.BookingFailed))
            }
        }
    }

    private fun handleValidationError(validation: AttendeeValidation) {
        val error =
            when (validation) {
                is AttendeeValidation.NikInvalid,
                is AttendeeValidation.NikSeatTaken,
                is AttendeeValidation.ServerDuplicateNik,
                -> DetailError.NikRejected
                is AttendeeValidation.DukcapilUnavailable -> DetailError.BookingFailed
                is AttendeeValidation.AdmissionInvalid -> {
                    sendEffect(DetailEffect.NavigateToQueue(eventId))
                    return
                }
                else -> DetailError.NikRejected
            }
        sendEffect(DetailEffect.ShowError(error))
        setState { copy(isSubmitting = false) }
    }

    /** Still Ready with time left; flips to Expired when the window closed during an await. */
    private fun isOrderActive(): Boolean {
        val ready = currentState.phase == DetailPhase.Ready
        val timeLeft = remainingSeconds(currentState.expiresAtMillis, serverClock.nowMillis()) > 0
        if (ready && !timeLeft) expire()
        return ready && timeLeft
    }
}
