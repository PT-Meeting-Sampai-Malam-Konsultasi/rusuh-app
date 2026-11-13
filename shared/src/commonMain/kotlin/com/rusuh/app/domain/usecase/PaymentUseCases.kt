package com.rusuh.app.domain.usecase

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.PaymentStatusResult
import com.rusuh.app.domain.model.PaymentSummary
import com.rusuh.app.domain.model.SnapTransaction
import com.rusuh.app.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow

class LoadPaymentUseCase(private val repository: PaymentRepository) {
    suspend operator fun invoke(bookingId: String): DomainResult<PaymentSummary> = repository.loadPayment(bookingId)
}

class StartSnapPaymentUseCase(private val repository: PaymentRepository) {
    suspend operator fun invoke(bookingId: String): DomainResult<SnapTransaction> = repository.startSnap(bookingId)
}

class ObservePaymentStatusUseCase(private val repository: PaymentRepository) {
    operator fun invoke(bookingId: String): Flow<PaymentStatusResult> = repository.observePaymentStatus(bookingId)
}
