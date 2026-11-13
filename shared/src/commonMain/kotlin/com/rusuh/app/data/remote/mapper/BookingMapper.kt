package com.rusuh.app.data.remote.mapper

import com.rusuh.app.data.clock.parseRfc3339Millis
import com.rusuh.app.data.remote.dto.BookingDto
import com.rusuh.app.data.remote.dto.CategoriesResponseDto
import com.rusuh.app.data.remote.dto.CategoryDto
import com.rusuh.app.domain.model.Booking
import com.rusuh.app.domain.model.BookingStatus
import com.rusuh.app.domain.model.CategoryAvailability
import com.rusuh.app.domain.model.SeatCategory

/** Maps a [CategoryDto] from the API to a domain [SeatCategory]. */
fun CategoryDto.toDomain(): SeatCategory =
    SeatCategory(
        id = categoryId,
        name = name,
        description = description,
        priceRupiah = price.amount,
        stock = available,
        entryGate = entryGate,
        availability = CategoryAvailability.fromWire(availability),
    )

/** Maps a [CategoriesResponseDto] to a list of [SeatCategory]. */
fun CategoriesResponseDto.toDomainCategories(): List<SeatCategory> = categories.map { it.toDomain() }

/** Parses expiresAt and serverNow from a [CategoriesResponseDto] and returns the expiry in epoch millis. */
fun CategoriesResponseDto.expiresAtMillis(): Long = parseRfc3339Millis(expiresAt) ?: 0L

/** Parses serverNow from a [CategoriesResponseDto] and returns epoch millis. */
fun CategoriesResponseDto.serverNowMillis(): Long = parseRfc3339Millis(serverNow) ?: 0L

/** Maps a [BookingDto] to a domain [Booking]. */
fun BookingDto.toDomain(): Booking =
    Booking(
        bookingId = bookingId,
        eventId = eventId,
        status = BookingStatus.fromWire(status),
        orderCode = orderCode,
        expiresAtMillis = expiresAt?.let(::parseRfc3339Millis),
        totalRupiah = price.total.amount,
    )
