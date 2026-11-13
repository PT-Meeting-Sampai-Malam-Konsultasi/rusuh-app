package com.rusuh.app.feature.detail

import com.rusuh.app.data.network.newIdempotencyKey
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.AttendeeValidation
import com.rusuh.app.domain.model.BookingResult
import com.rusuh.app.domain.model.isRejected
import com.rusuh.app.domain.model.isValid
import com.rusuh.app.domain.model.validateAttendeeLocally
import com.rusuh.app.domain.usecase.CreateBookingUseCase
import com.rusuh.app.domain.usecase.GetDetailUseCase
import com.rusuh.app.domain.usecase.ValidateAttendeeUseCase
import com.rusuh.app.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/** Debounce delay for NIK validation in milliseconds. Named for test virtual time. */
const val NIK_VALIDATION_DEBOUNCE_MS = 600L

/**
 * MVI ViewModel for the Detail Ticket page.
 *
 * Talks to the data layer through use cases only. The countdown is recomputed on every
 * [DetailIntent.Tick] as `expiresAt - serverClock.now` (never the device clock); the host decides
 * how often to tick.
 *
 * NIK validation happens inline: after the user stops typing for [NIK_VALIDATION_DEBOUNCE_MS],
 * a local check runs (format, duplicate). If it passes, the server validation API is called.
 * The result is shown inline and a valid token is cached for the booking request.
 *
 * @param eventId the event this order is for.
 * @param serverClock server-time source (fake in tests).
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
            DetailIntent.Load -> if (!loadStarted) load()
            DetailIntent.Retry -> if (currentState.phase is DetailPhase.Failed) load()
            DetailIntent.Back -> sendEffect(DetailEffect.NavigateBack)
            DetailIntent.GoHome -> sendEffect(DetailEffect.NavigateHome)
            DetailIntent.QueueAgain -> sendEffect(DetailEffect.NavigateToQueue(eventId))
            DetailIntent.ChangeEmail -> Unit
            DetailIntent.Tick -> tick()
            DetailIntent.Proceed -> proceed()
            is DetailIntent.IncrementCategory -> changeQuantity(intent.categoryId, +1)
            is DetailIntent.DecrementCategory -> changeQuantity(intent.categoryId, -1)
            is DetailIntent.AttendeeNameChanged -> onAttendeeChanged(intent.index, name = intent.name)
            is DetailIntent.AttendeeNikChanged -> onAttendeeChanged(intent.index, nik = intent.nik)
            is DetailIntent.RetryValidation -> retryValidation(intent.index)
        }
    }

    private var loadStarted = false

    private var idempotencyKey: String = newIdempotencyKey()

    private val validationJobs = mutableMapOf<Int, Job>()

    private val validatedAttendees = mutableMapOf<Int, CachedValidation>()

    private val proceedMutex = Mutex()

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
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            setState { copy(phase = DetailPhase.Failed(DetailError.LoadFailed)) }
        }
    }

    private fun tick() {
        if (currentState.phase != DetailPhase.Ready) return
        val remaining = remainingSeconds(currentState.expiresAtMillis, serverClock.nowMillis())
        if (remaining <= 0) expire() else setState { copy(remainingSeconds = remaining) }
    }

    private fun expire() {
        validationJobs.values.forEach { it.cancel() }
        validationJobs.clear()
        validatedAttendees.clear()
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

    @Suppress("ReturnCount")
    private fun onAttendeeChanged(
        index: Int,
        name: String? = null,
        nik: String? = null,
    ) {
        val state = currentState
        val content = state.detail
        if (state.phase != DetailPhase.Ready || content == null || state.isSubmitting) return

        val oldAttendee = state.form.attendees.getOrNull(index) ?: return
        val newName = name ?: oldAttendee.name
        val newNik = nik?.filter(::isAsciiDigit)?.take(NIK_LENGTH) ?: oldAttendee.nik

        if (hasCachedValidation(index, newName, newNik, oldAttendee.categoryId)) {
            applyFromCache(index, newName, newNik)
            return
        }

        invalidateAndUpdate(index, name, nik)
        scheduleValidation(index)
    }

    private fun hasCachedValidation(
        index: Int,
        name: String,
        nik: String,
        categoryId: String,
    ): Boolean {
        val cachedKey = CacheKey(name.trim(), nik, categoryId)
        val cached = validatedAttendees[index]
        return cached != null && cached.key == cachedKey && cached.token.isNotEmpty()
    }

    private fun applyFromCache(
        index: Int,
        name: String,
        nik: String,
    ) {
        setState {
            copy(
                form =
                    form.withName(index, name)
                        .withNik(index, nik)
                        .withValidationStatus(index, AttendeeValidationStatus.Valid),
            )
        }
    }

    private fun invalidateAndUpdate(
        index: Int,
        name: String?,
        nik: String?,
    ) {
        validationJobs[index]?.cancel()
        validatedAttendees.remove(index)

        setState {
            copy(
                form =
                    when {
                        name != null -> form.withName(index, name)
                        else -> form.withNik(index, nik ?: "")
                    },
            )
        }
    }

    private fun scheduleValidation(index: Int) {
        validationJobs[index]?.cancel()
        validationJobs[index] =
            viewModelScope.launch {
                delay(NIK_VALIDATION_DEBOUNCE_MS)
                runValidationForAttendee(index)
            }
    }

    @Suppress("ReturnCount")
    private suspend fun runValidationForAttendee(index: Int) {
        val state = currentState
        if (state.phase != DetailPhase.Ready || state.isSubmitting) return

        val attendee = state.form.attendees.getOrNull(index) ?: return
        val earlierNiks = state.form.attendees.take(index).map { it.nik }
        val localResult = validateAttendeeLocally(attendee.name, attendee.nik, earlierNiks)

        if (!localResult.isValid) return

        setState { copy(form = form.withValidationStatus(index, AttendeeValidationStatus.Checking)) }

        val result = callServerValidation(index, attendee, earlierNiks)
        if (result != null && isAttendeeUnchanged(index, attendee)) {
            applyValidationResult(index, attendee, result)
        }
    }

    @Suppress("SwallowedException")
    private suspend fun callServerValidation(
        index: Int,
        attendee: AttendeeForm,
        earlierNiks: List<String>,
    ): AttendeeValidation? {
        return try {
            validateAttendee(eventId, index, attendee.name.trim(), attendee.nik, earlierNiks)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            setState { copy(form = form.withValidationStatus(index, AttendeeValidationStatus.Unavailable)) }
            null
        }
    }

    private fun isAttendeeUnchanged(
        index: Int,
        original: AttendeeForm,
    ): Boolean {
        val current = currentState.form.attendees.getOrNull(index)
        return current?.nik == original.nik && current.name == original.name
    }

    private fun applyValidationResult(
        index: Int,
        attendee: AttendeeForm,
        result: AttendeeValidation,
    ) {
        when {
            result.isValid -> {
                val token = (result as AttendeeValidation.Valid).validationToken
                validatedAttendees[index] =
                    CachedValidation(
                        CacheKey(attendee.name.trim(), attendee.nik, attendee.categoryId),
                        token,
                    )
                setState { copy(form = form.withValidationStatus(index, AttendeeValidationStatus.Valid)) }
            }
            result.isRejected -> {
                val reason = result.toRejectedReason() ?: RejectedReason.NIK_INVALID
                setState {
                    copy(form = form.withValidationStatus(index, AttendeeValidationStatus.Rejected(reason)))
                }
            }
            result is AttendeeValidation.AdmissionInvalid -> {
                sendEffect(DetailEffect.NavigateToQueue(eventId))
            }
            else -> {
                setState { copy(form = form.withValidationStatus(index, AttendeeValidationStatus.Unavailable)) }
            }
        }
    }

    private fun retryValidation(index: Int) {
        scheduleValidation(index)
    }

    private fun editForm(edit: (DetailContent, DetailFormState) -> DetailFormState) {
        val state = currentState
        val content = state.detail
        if (state.phase != DetailPhase.Ready || content == null || state.isSubmitting) return
        setState { copy(form = edit(content, form)) }
    }

    @Suppress("SwallowedException", "LongMethod", "CyclomaticComplexMethod")
    private suspend fun proceed() {
        val canProceed =
            proceedMutex.withLock {
                if (!canStartProceed()) {
                    false
                } else {
                    setState { copy(isSubmitting = true) }
                    true
                }
            }
        if (!canProceed) return
        try {
            executeProceedFlow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            if (isOrderActive()) {
                sendEffect(DetailEffect.ShowError(DetailError.ServiceUnavailable))
            }
        } finally {
            setState { copy(isSubmitting = false) }
        }
    }

    @Suppress("ReturnCount")
    private fun canStartProceed(): Boolean {
        val state = currentState
        if (state.isSubmitting) return false
        val content = state.detail
        val ready = state.phase == DetailPhase.Ready && content != null && state.canProceed
        if (!ready) return false
        val hasTime = remainingSeconds(state.expiresAtMillis, serverClock.nowMillis()) > 0
        if (!hasTime) expire()
        return hasTime
    }

    private suspend fun executeProceedFlow() {
        val form = currentState.form

        val validationResult = ensureAllAttendeesValidated(form)
        if (validationResult != null) {
            handleValidationError(validationResult)
            return
        }

        if (isOrderActive()) {
            createBookingAndHandleResult(form)
        }
    }

    private suspend fun ensureAllAttendeesValidated(form: DetailFormState): AttendeeValidation? {
        for (i in form.attendees.indices) {
            val attendee = form.attendees[i]
            val cacheKey = CacheKey(attendee.name.trim(), attendee.nik, attendee.categoryId)
            val cached = validatedAttendees[i]

            if (cached != null && cached.key == cacheKey && cached.token.isNotEmpty()) {
                continue
            }

            val earlierNiks = form.attendees.take(i).map { it.nik }
            val validation = validateAttendee(eventId, i, attendee.name.trim(), attendee.nik, earlierNiks)

            if (!validation.isValid) {
                return validation
            }

            val token = (validation as AttendeeValidation.Valid).validationToken
            validatedAttendees[i] = CachedValidation(cacheKey, token)
        }
        return null
    }

    private suspend fun createBookingAndHandleResult(form: DetailFormState) {
        val tickets =
            form.attendees.mapIndexed { i, a ->
                val cached = checkNotNull(validatedAttendees[i]) { "Missing validation token for attendee $i" }
                ValidatedAttendee(a.categoryId, a.name.trim(), a.nik, cached.token)
            }
        val bookingRequest = form.toBookingRequestWithTokens(eventId, tickets)

        val result = createBooking(bookingRequest, idempotencyKey)

        if (!isOrderActive()) {
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
                idempotencyKey = newIdempotencyKey()
                sendEffect(DetailEffect.ShowError(DetailError.BookingFailed))
            }
            is BookingResult.CategorySoldOut -> {
                idempotencyKey = newIdempotencyKey()
                sendEffect(DetailEffect.ShowError(DetailError.BookingFailed))
            }
            is BookingResult.AdmissionInvalid,
            is BookingResult.SessionExpired,
            -> {
                idempotencyKey = newIdempotencyKey()
                sendEffect(DetailEffect.NavigateToQueue(eventId))
            }
            is BookingResult.NetworkError,
            is BookingResult.ServiceUnavailable,
            is BookingResult.RateLimited,
            -> {
                sendEffect(DetailEffect.ShowError(DetailError.ServiceUnavailable))
            }
            is BookingResult.AttendeeNotValidated -> {
                validatedAttendees.clear()
                idempotencyKey = newIdempotencyKey()
                sendEffect(DetailEffect.ShowError(DetailError.NikRejected))
            }
            is BookingResult.QuantityOverReservation,
            is BookingResult.NikSeatTaken,
            is BookingResult.DuplicateNik,
            is BookingResult.BookingNotHeld,
            is BookingResult.ServerError,
            -> {
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

    private fun isOrderActive(): Boolean {
        val ready = currentState.phase == DetailPhase.Ready
        val timeLeft = remainingSeconds(currentState.expiresAtMillis, serverClock.nowMillis()) > 0
        if (ready && !timeLeft) expire()
        return ready && timeLeft
    }

    private data class CacheKey(val name: String, val nik: String, val categoryId: String) {
        override fun toString(): String = "CacheKey(categoryId=$categoryId, name=<redacted>, nik=<redacted>)"
    }

    private data class CachedValidation(val key: CacheKey, val token: String) {
        override fun toString(): String = "CachedValidation(key=$key, token=<redacted>)"
    }
}
