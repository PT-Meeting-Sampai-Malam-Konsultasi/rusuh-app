package com.rusuh.app.data.remote.mapper

import com.rusuh.app.data.remote.dto.AttendeeDto
import com.rusuh.app.data.remote.dto.BookingDto
import com.rusuh.app.data.remote.dto.EnterPaymentDto
import com.rusuh.app.data.remote.dto.PriceBreakdownDto
import com.rusuh.app.data.remote.dto.PriceLineDto
import com.rusuh.app.domain.model.AttendeeInfo
import com.rusuh.app.domain.model.BookingStatus
import com.rusuh.app.domain.model.PaymentStatus
import com.rusuh.app.domain.model.PaymentStatusResult
import com.rusuh.app.domain.model.PaymentSummary
import com.rusuh.app.domain.model.PriceBreakdown
import com.rusuh.app.domain.model.PriceLine
import com.rusuh.app.domain.model.SnapTransaction
import kotlinx.datetime.Instant

/** Maps the booking record and local account email to the payment summary. */
fun BookingDto.toPaymentSummary(customerEmail: String?): PaymentSummary =
    PaymentSummary(
        bookingId = bookingId,
        orderCode = orderCode.orEmpty(),
        eventName = null,
        eventStartsAt = null,
        venueShort = null,
        timezone = null,
        attendees = attendees.map { it.toDomain() },
        customerEmail = customerEmail,
        price = price.toDomain(),
        expiresAt = (expiresAt ?: serverNow).parseInstant(),
        serverNow = serverNow.parseInstant(),
        bookingStatus = BookingStatus.fromWire(status),
        refundRequired = refundRequired,
    )

/** Maps a payment entry response to the web handoff model. */
fun EnterPaymentDto.toDomain(): SnapTransaction =
    SnapTransaction(
        bookingId = bookingId,
        snapToken = snapToken,
        redirectUrl = redirectUrl,
        paymentExpiresAt = paymentExpiresAt.parseInstant(),
    )

/** Maps the authoritative booking returned by status checks. */
fun BookingDto.toPaymentStatus(): PaymentStatusResult =
    PaymentStatusResult(
        bookingStatus = BookingStatus.fromWire(status),
        paymentStatus =
            when {
                status.equals("PAID", true) -> PaymentStatus.SETTLED
                refundRequired -> PaymentStatus.PAID_AFTER_EXPIRY
                status.equals("PAYMENT_PENDING", true) -> PaymentStatus.PENDING
                else -> PaymentStatus.NONE
            },
        returnUrl = null,
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
