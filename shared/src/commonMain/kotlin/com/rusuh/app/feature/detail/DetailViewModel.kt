package com.rusuh.app.feature.detail

import com.rusuh.app.domain.clock.ServerClock
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
            is DetailIntent.AttendeeKindChanged ->
                editForm { content, form ->
                    form.withKind(intent.index, intent.kind, content.accountName)
                }
            is DetailIntent.AttendeeNameChanged -> editForm { _, form -> form.withName(intent.index, intent.name) }
            is DetailIntent.AttendeeNikChanged -> editForm { _, form -> form.withNik(intent.index, intent.nik) }
        }
    }

    private var loadStarted = false

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

    /** Window elapsed: drop the selection and attendee data (PII) and show the expired sheet. */
    private fun expire() {
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

    @Suppress("SwallowedException")
    private suspend fun proceed() {
        val state = currentState
        val content = state.detail
        if (state.phase != DetailPhase.Ready || content == null || !state.canProceed) return
        if (remainingSeconds(state.expiresAtMillis, serverClock.nowMillis()) <= 0) {
            expire()
            return
        }
        setState { copy(isSubmitting = true) }
        val form = state.form
        try {
            val allValid =
                form.attendees.indices.all { i ->
                    val a = form.attendees[i]
                    validateAttendee(a.name, a.nik, form.attendees.take(i).map { it.nik }).isValid
                }
            // TODO(FR-16-M1 #81): real booking + stock hold; the stub returns a fake id.
            val booking = if (allValid) createBooking(form.toBookingRequest(eventId)) else null
            if (isOrderActive()) {
                if (booking != null) {
                    // TODO(FR-16-M1 #81): the payment route does not exist yet; the host ignores this.
                    sendEffect(DetailEffect.NavigateToPayment(booking.bookingId))
                } else {
                    sendEffect(DetailEffect.ShowError(DetailError.NikRejected))
                }
            }
            // else: the window closed while awaiting. TODO(FR-16-M1 #81): release the stock hold
            // of [booking] here; emit nothing so the expired sheet is not raced by navigation.
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            // Intentionally broad: any repository/network failure maps to a transient error.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            if (isOrderActive()) sendEffect(DetailEffect.ShowError(DetailError.BookingFailed))
        } finally {
            setState { copy(isSubmitting = false) }
        }
    }

    /** Still Ready with time left; flips to Expired when the window closed during an await. */
    private fun isOrderActive(): Boolean {
        val ready = currentState.phase == DetailPhase.Ready
        val timeLeft = remainingSeconds(currentState.expiresAtMillis, serverClock.nowMillis()) > 0
        if (ready && !timeLeft) expire()
        return ready && timeLeft
    }
}
