package com.rusuh.app.feature.payment

import com.rusuh.app.domain.model.BookingStatus
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import com.rusuh.app.domain.model.PaymentStatus
import com.rusuh.app.domain.model.PaymentSummary
import com.rusuh.app.domain.usecase.LoadPaymentUseCase
import com.rusuh.app.domain.usecase.ObservePaymentStatusUseCase
import com.rusuh.app.domain.usecase.StartSnapPaymentUseCase
import com.rusuh.app.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * MVI ViewModel for the Payment screen.
 *
 * Manages the payment flow: load summary → open Snap → poll status → show result.
 * Countdowns are driven by server time, not the device clock.
 */
@Suppress("TooManyFunctions") // Split for readability and to keep cyclomatic complexity low per function
class PaymentViewModel(
    private val bookingId: String,
    private val loadPayment: LoadPaymentUseCase,
    private val startSnapPayment: StartSnapPaymentUseCase,
    private val observePaymentStatus: ObservePaymentStatusUseCase,
    private val monotonicMillis: () -> Long = defaultMonotonicMillis(),
) : MviViewModel<PaymentContract.State, PaymentIntent, PaymentEffect>(PaymentContract.State()) {
    private var pollingJob: Job? = null
    private var summary: PaymentSummary? = null
    private var serverNowAtReceive: Instant = Instant.DISTANT_PAST
    private var receivedAtMillis: Long = 0L
    private var lastIdempotencyKey: String? = null
    private var backSent = false

    override suspend fun reduce(intent: PaymentIntent) {
        when (intent) {
            PaymentIntent.Load -> load()
            PaymentIntent.Retry -> load()
            PaymentIntent.Back -> sendBackOnce()
            PaymentIntent.OpenSnap -> openSnap()
            PaymentIntent.RetrySnap -> openSnap()
            PaymentIntent.Tick -> tick()
            PaymentIntent.Resumed -> refreshAfterResume()
            PaymentIntent.GoToTickets -> sendEffect(PaymentEffect.NavigateToTickets)
            PaymentIntent.QueueAgain -> queueAgain()
            PaymentIntent.GoHome -> sendEffect(PaymentEffect.NavigateToHome)
            PaymentIntent.SnapClosed -> refreshAfterResume()
        }
    }

    private suspend fun load() {
        setState { copy(ui = PaymentUiState.Loading) }
        when (val result = loadPayment(bookingId)) {
            is DomainResult.Success -> {
                handleLoadSuccess(result.data)
                if (result.data.currentSnap != null) {
                    checkStatusOnce(result.data)
                }
            }
            is DomainResult.Error -> handleLoadError(result.errorCode)
            is DomainResult.NetworkError -> setNetworkError()
            is DomainResult.Empty, is DomainResult.TechnicalError -> setGenericError()
        }
    }

    private fun handleLoadSuccess(data: PaymentSummary) {
        summary = data
        serverNowAtReceive = data.serverNow
        receivedAtMillis = monotonicMillis()
        val remaining = computeRemainingSeconds(data.expiresAt)

        if (remaining <= 0) {
            setExpiredState(data.toUi(), data.customerEmail, refundRequired = false)
        } else {
            val hasOpenedSnap = data.currentSnap != null
            setState {
                copy(
                    ui =
                        PaymentUiState.Ready(
                            summary = data.toUi(),
                            hasOpenedSnap = hasOpenedSnap,
                        ),
                    remainingSeconds = remaining,
                )
            }
        }
    }

    private fun handleLoadError(errorCode: ErrorCode?) {
        val message =
            when (errorCode) {
                ErrorCode.BOOKING_NOT_HELD -> "Booking is no longer held"
                else -> "Could not load payment details"
            }
        setState { copy(ui = PaymentUiState.Error(message)) }
    }

    private fun setNetworkError() {
        setState { copy(ui = PaymentUiState.Error("Could not reach the server")) }
    }

    private fun setGenericError() {
        setState { copy(ui = PaymentUiState.Error("Something went wrong")) }
    }

    private fun setExpiredState(
        summaryUi: PaymentSummaryUi,
        customerEmail: String?,
        refundRequired: Boolean,
    ) {
        setState {
            copy(
                ui =
                    PaymentUiState.Expired(
                        summary = summaryUi,
                        customerEmail = customerEmail,
                        refundRequired = refundRequired,
                    ),
                remainingSeconds = 0,
            )
        }
    }

    private suspend fun openSnap() {
        val currentSummary = summary ?: return
        setState { copy(isSnapLaunching = true) }
        clearBusyError()

        val snapResult = fetchSnapResult()
        handleSnapResult(snapResult, currentSummary)
    }

    private fun clearBusyError() {
        val currentUi = currentState.ui
        if (currentUi is PaymentUiState.Ready && currentUi.busyError != null) {
            setState { copy(ui = currentUi.copy(busyError = null)) }
        }
    }

    private suspend fun fetchSnapResult(): DomainResult<com.rusuh.app.domain.model.SnapTransaction> =
        if (lastIdempotencyKey != null) {
            startSnapPayment.invokeWithKey(bookingId, lastIdempotencyKey!!)
        } else {
            val result = startSnapPayment.invoke(bookingId)
            lastIdempotencyKey = result.idempotencyKey
            result.result
        }

    private fun handleSnapResult(
        snapResult: DomainResult<com.rusuh.app.domain.model.SnapTransaction>,
        currentSummary: PaymentSummary,
    ) {
        setState { copy(isSnapLaunching = false) }

        when (snapResult) {
            is DomainResult.Success -> {
                markSnapOpened()
                sendEffect(PaymentEffect.LaunchSnap(snapResult.data.redirectUrl))
            }
            is DomainResult.Error -> handleSnapError(snapResult.errorCode, snapResult.retryAfterSeconds, currentSummary)
            is DomainResult.NetworkError -> setBusyErrorAndClearKey()
            is DomainResult.Empty, is DomainResult.TechnicalError -> lastIdempotencyKey = null
        }
    }

    private fun markSnapOpened() {
        val ui = currentState.ui
        if (ui is PaymentUiState.Ready) {
            setState { copy(ui = ui.copy(hasOpenedSnap = true)) }
        }
    }

    private fun handleSnapError(
        errorCode: ErrorCode?,
        retryAfterSeconds: Int?,
        currentSummary: PaymentSummary,
    ) {
        when (errorCode) {
            ErrorCode.PAYMENT_PROVIDER_BUSY -> setBusyError(retryAfterSeconds)
            ErrorCode.BOOKING_NOT_HELD -> setExpiredState(currentSummary.toUi(), currentSummary.customerEmail, false)
            else -> setBusyError(null)
        }
        lastIdempotencyKey = null
    }

    private fun setBusyError(retryAfterSeconds: Int?) {
        val ui = currentState.ui
        if (ui is PaymentUiState.Ready) {
            setState { copy(ui = ui.copy(busyError = BusyError(retryAfterSeconds))) }
        }
    }

    private fun setBusyErrorAndClearKey() {
        setBusyError(null)
        lastIdempotencyKey = null
    }

    @Suppress("ReturnCount", "SwallowedException")
    private suspend fun refreshAfterResume() {
        val previous = summary ?: return
        val ui = currentState.ui
        if (ui !is PaymentUiState.Ready || !ui.hasOpenedSnap) return
        pollingJob?.cancel()
        when (val refreshed = loadPayment(bookingId)) {
            is DomainResult.Success -> {
                summary = refreshed.data
                serverNowAtReceive = refreshed.data.serverNow
                receivedAtMillis = monotonicMillis()
                val remaining = computeRemainingSeconds(refreshed.data.expiresAt)
                setState { copy(remainingSeconds = remaining) }
                checkStatusOnce(refreshed.data)
            }
            is DomainResult.Error,
            is DomainResult.NetworkError,
            is DomainResult.Empty,
            is DomainResult.TechnicalError,
            -> {
                val remaining = computeRemainingSeconds(previous.expiresAt)
                setState { copy(remainingSeconds = remaining) }
                if (remaining == 0) setExpiredState(previous.toUi(), previous.customerEmail, false)
            }
        }
    }

    @Suppress("SwallowedException")
    private suspend fun checkStatusOnce(currentSummary: PaymentSummary) {
        val status =
            try {
                observePaymentStatus(bookingId).first()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                setGenericError()
                return
            }
        serverNowAtReceive = status.serverNow
        receivedAtMillis = monotonicMillis()
        when {
            status.bookingStatus.isPaid -> handlePollingStatus(status.bookingStatus, true, currentSummary)
            status.bookingStatus == BookingStatus.EXPIRED || status.bookingStatus == BookingStatus.CANCELLED ->
                handlePollingStatus(
                    status.bookingStatus,
                    status.paymentStatus == PaymentStatus.PAID_AFTER_EXPIRY,
                    currentSummary,
                )
            computeRemainingSeconds(currentSummary.expiresAt) <= 0 ->
                setExpiredState(currentSummary.toUi(), currentSummary.customerEmail, false)
            else ->
                setState {
                    copy(
                        ui = PaymentUiState.Ready(currentSummary.toUi(), hasOpenedSnap = true),
                        remainingSeconds = computeRemainingSeconds(currentSummary.expiresAt),
                    )
                }
        }
    }

    private fun handlePollingStatus(
        bookingStatus: BookingStatus,
        paymentSuccess: Boolean,
        currentSummary: PaymentSummary,
    ) {
        when {
            bookingStatus.isPaid -> {
                setState {
                    copy(
                        ui =
                            PaymentUiState.Success(
                                orderCode = currentSummary.orderCode,
                                eventName = currentSummary.eventName,
                                ticketCount = currentSummary.attendees.size,
                                bookingId = bookingId,
                            ),
                    )
                }
                pollingJob?.cancel()
            }
            bookingStatus == BookingStatus.EXPIRED || bookingStatus == BookingStatus.CANCELLED -> {
                setExpiredState(currentSummary.toUi(), currentSummary.customerEmail, refundRequired = paymentSuccess)
                pollingJob?.cancel()
            }
            // Still pending: continue polling
        }
    }

    private fun tick() {
        val currentSummary = summary ?: return
        val remaining = computeRemainingSeconds(currentSummary.expiresAt)
        setState { copy(remainingSeconds = remaining) }

        if (remaining <= 0) {
            pollingJob?.cancel()
            val ui = currentState.ui
            if (ui !is PaymentUiState.Success && ui !is PaymentUiState.Expired) {
                setExpiredState(currentSummary.toUi(), currentSummary.customerEmail, refundRequired = false)
            }
        }
    }

    private fun computeRemainingSeconds(expiresAt: Instant): Int {
        val elapsed = (monotonicMillis() - receivedAtMillis) / 1000
        val serverNow = serverNowAtReceive + elapsed.seconds
        return (expiresAt - serverNow).inWholeSeconds.toInt().coerceAtLeast(0)
    }

    private fun sendBackOnce() {
        if (backSent) return
        backSent = true
        pollingJob?.cancel()
        sendEffect(PaymentEffect.NavigateBack)
    }

    private fun queueAgain() {
        sendEffect(PaymentEffect.NavigateToHome)
    }
}

private fun PaymentSummary.toUi(): PaymentSummaryUi =
    PaymentSummaryUi(
        bookingId = bookingId,
        orderCode = orderCode,
        eventName = eventName,
        eventMeta = buildEventMeta(),
        attendees = attendees.toUi(price),
        price = price.toUi(),
        customerEmail = customerEmail,
    )

private fun PaymentSummary.buildEventMeta(): String? {
    val parts = listOfNotNull(eventName, venueShort)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun defaultMonotonicMillis(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}
