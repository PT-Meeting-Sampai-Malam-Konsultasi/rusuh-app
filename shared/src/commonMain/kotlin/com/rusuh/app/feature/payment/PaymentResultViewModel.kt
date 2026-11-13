package com.rusuh.app.feature.payment

import com.rusuh.app.domain.model.BookingStatus
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.PaymentStatus
import com.rusuh.app.domain.usecase.LoadPaymentUseCase
import com.rusuh.app.domain.usecase.ObservePaymentStatusUseCase
import com.rusuh.app.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the Payment Result screen.
 *
 * Reached via App Link / Universal Link after returning from Midtrans Snap.
 * NEVER trusts the URL: only takes the bookingId and fetches the actual status.
 */
class PaymentResultViewModel(
    private val bookingId: String,
    private val loadPayment: LoadPaymentUseCase,
    private val observePaymentStatus: ObservePaymentStatusUseCase,
) : MviViewModel<PaymentResultContract.State, PaymentResultIntent, PaymentResultEffect>(
        PaymentResultContract.State(),
    ) {
    private var pollingJob: Job? = null

    override suspend fun reduce(intent: PaymentResultIntent) {
        when (intent) {
            PaymentResultIntent.Load -> load()
            PaymentResultIntent.Retry -> load()
            PaymentResultIntent.GoToTickets -> sendEffect(PaymentResultEffect.NavigateToTickets)
            PaymentResultIntent.QueueAgain, PaymentResultIntent.GoHome ->
                sendEffect(PaymentResultEffect.NavigateToHome)
        }
    }

    private suspend fun load() {
        setState { copy(ui = PaymentResultUiState.Loading) }

        // First, try to get the current status
        try {
            val statusResult = observePaymentStatus(bookingId).first()

            when {
                statusResult.bookingStatus.isPaid -> {
                    // Fetch full payment info for display
                    val paymentResult = loadPayment(bookingId)
                    val summary = (paymentResult as? DomainResult.Success)?.data
                    setState {
                        copy(
                            ui =
                                PaymentResultUiState.Success(
                                    orderCode = summary?.orderCode ?: "",
                                    eventName = summary?.eventName,
                                    ticketCount = summary?.attendees?.size ?: 0,
                                    bookingId = bookingId,
                                ),
                        )
                    }
                }
                statusResult.bookingStatus == BookingStatus.EXPIRED ||
                    statusResult.bookingStatus == BookingStatus.CANCELLED -> {
                    val refundRequired = statusResult.paymentStatus == PaymentStatus.PAID_AFTER_EXPIRY
                    // Try to get customer email from payment info
                    val paymentResult = loadPayment(bookingId)
                    val summary = (paymentResult as? DomainResult.Success)?.data
                    setState {
                        copy(
                            ui =
                                PaymentResultUiState.Expired(
                                    orderCode = summary?.orderCode,
                                    customerEmail = summary?.customerEmail,
                                    refundRequired = refundRequired,
                                ),
                        )
                    }
                }
                else -> {
                    // Still pending - poll until final
                    startPolling()
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Failed to get status - show error with cause logged
            setState {
                copy(
                    ui = PaymentResultUiState.Error("Could not verify payment status: ${e.message ?: "unknown error"}"),
                )
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()

        setState { copy(ui = PaymentResultUiState.Loading) }

        pollingJob =
            viewModelScope.launch {
                observePaymentStatus(bookingId)
                    .catch { e ->
                        setState { copy(ui = PaymentResultUiState.Error("Could not verify payment status")) }
                    }
                    .collect { status ->
                        when {
                            status.bookingStatus.isPaid -> {
                                // Fetch full payment info for display
                                val paymentResult = loadPayment(bookingId)
                                val summary = (paymentResult as? DomainResult.Success)?.data
                                setState {
                                    copy(
                                        ui =
                                            PaymentResultUiState.Success(
                                                orderCode = summary?.orderCode ?: "",
                                                eventName = summary?.eventName,
                                                ticketCount = summary?.attendees?.size ?: 0,
                                                bookingId = bookingId,
                                            ),
                                    )
                                }
                                pollingJob?.cancel()
                            }
                            status.bookingStatus == BookingStatus.EXPIRED ||
                                status.bookingStatus == BookingStatus.CANCELLED -> {
                                val refundRequired = status.paymentStatus == PaymentStatus.PAID_AFTER_EXPIRY
                                val paymentResult = loadPayment(bookingId)
                                val summary = (paymentResult as? DomainResult.Success)?.data
                                setState {
                                    copy(
                                        ui =
                                            PaymentResultUiState.Expired(
                                                orderCode = summary?.orderCode,
                                                customerEmail = summary?.customerEmail,
                                                refundRequired = refundRequired,
                                            ),
                                    )
                                }
                                pollingJob?.cancel()
                            }
                            // Still pending: continue polling
                        }
                    }
            }
    }
}
