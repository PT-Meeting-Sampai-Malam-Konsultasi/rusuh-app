package com.rusuh.app.data.remote.mapper

import com.rusuh.app.data.remote.dto.AttendeeDto
import com.rusuh.app.data.remote.dto.PaymentStatusDto
import com.rusuh.app.data.remote.dto.PaymentViewDto
import com.rusuh.app.data.remote.dto.PriceBreakdownDto
import com.rusuh.app.data.remote.dto.PriceLineDto
import com.rusuh.app.data.remote.dto.SnapTransactionDto
import com.rusuh.app.domain.model.AttendeeInfo
import com.rusuh.app.domain.model.BookingStatus
import com.rusuh.app.domain.model.PaymentMethod
import com.rusuh.app.domain.model.PaymentStatus
import com.rusuh.app.domain.model.PaymentStatusResult
import com.rusuh.app.domain.model.PaymentSummary
import com.rusuh.app.domain.model.PriceBreakdown
import com.rusuh.app.domain.model.PriceLine
import com.rusuh.app.domain.model.SnapTransaction
import kotlinx.datetime.Instant

/** Maps [PaymentViewDto] to [PaymentSummary]. */
fun PaymentViewDto.toDomain(): PaymentSummary =
    PaymentSummary(
        bookingId = bookingId,
        orderCode = orderCode,
        eventName = eventName,
        eventStartsAt = eventStartsAt?.parseInstantOrNull(),
        venueShort = venueShort,
        timezone = timezone,
        attendees = attendees.map { it.toDomain() },
        customerEmail = customerEmail,
        price = price.toDomain(),
        currentSnap = currentSnap?.toDomain(),
        expiresAt = expiresAt.parseInstant(),
        serverNow = serverNow.parseInstant(),
        snapClientKey = snapClientKey,
    )

/** Maps [SnapTransactionDto] to [SnapTransaction]. */
fun SnapTransactionDto.toDomain(): SnapTransaction =
    SnapTransaction(
        attemptId = attemptId,
        orderId = orderId,
        method = PaymentMethod.fromWire(method),
        snapToken = snapToken,
        redirectUrl = redirectUrl,
        gatewayExpiresAt = gatewayExpiresAt.parseInstant(),
    )

/** Maps [PaymentStatusDto] to [PaymentStatusResult]. */
fun PaymentStatusDto.toDomain(): PaymentStatusResult =
    PaymentStatusResult(
        bookingStatus = BookingStatus.fromWire(bookingStatus),
        paymentStatus = PaymentStatus.fromWire(paymentStatus),
        returnUrl = returnUrl,
        serverNow = serverNow.parseInstant(),
    )

/** Maps [AttendeeDto] to [AttendeeInfo]. */
fun AttendeeDto.toDomain(): AttendeeInfo =
    AttendeeInfo(
        categoryId = categoryId,
        categoryName = categoryName,
        fullName = fullName,
        nikMasked = nikMasked,
    )

/** Maps [PriceBreakdownDto] to [PriceBreakdown]. */
fun PriceBreakdownDto.toDomain(): PriceBreakdown =
    PriceBreakdown(
        lines = lines.map { it.toDomain() },
        ticketSubtotal = ticketSubtotal.amount,
        adminFee = adminFee.amount,
        tax = tax?.amount,
        total = total.amount,
    )

/** Maps [PriceLineDto] to [PriceLine]. */
fun PriceLineDto.toDomain(): PriceLine =
    PriceLine(
        categoryId = categoryId,
        name = name,
        quantity = quantity,
        unitPrice = unitPrice.amount,
        lineTotal = lineTotal.amount,
    )

/** Parses an ISO 8601 instant string; throws on invalid input. */
private fun String.parseInstant(): Instant = Instant.parse(this)

/** Parses an ISO 8601 instant string; null on invalid or blank input. */
private fun String.parseInstantOrNull(): Instant? =
    takeIf { it.isNotBlank() }?.runCatching { Instant.parse(this) }?.getOrNull()
