package com.rusuh.app.data.remote.dto

import com.rusuh.app.domain.model.BookingRequest
import com.rusuh.app.domain.model.BookingTicket
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that classes carrying NIK (PII) redact it in their toString() to prevent
 * accidental leaks in logs or error messages.
 *
 * NIK is the Indonesian national ID number (16 digits) that must never appear in logs.
 */
class NikRedactionTest {
    private val testNik = "3174055203960004"
    private val partialNik = "3174055203" // A partial match is also a leak

    @Test
    fun validateAttendeeRequestDtoRedactsNikInToString() {
        val dto =
            ValidateAttendeeRequestDto(
                fieldIndex = 0,
                fullName = "Nadia Saputri",
                nik = testNik,
            )

        val stringRep = dto.toString()

        // The NIK should not appear in the string representation
        assertFalse(
            stringRep.contains(testNik),
            "NIK should be redacted in toString(), but got: $stringRep",
        )
        assertFalse(
            stringRep.contains(partialNik),
            "Partial NIK should not appear in toString(), but got: $stringRep",
        )
        // Should contain the redacted marker
        assertTrue(
            stringRep.contains("<redacted>") || stringRep.contains("***"),
            "toString() should indicate the NIK is redacted, got: $stringRep",
        )
        // The other fields should still be visible for debugging
        assertTrue(stringRep.contains("fieldIndex=0"), "fieldIndex should be in toString()")
        assertTrue(stringRep.contains("fullName=Nadia Saputri") || stringRep.contains("Nadia Saputri"))
    }

    @Test
    fun bookingTicketRedactsNikInToString() {
        val ticket =
            BookingTicket(
                categoryId = "cat_vip",
                holderName = "Nadia Saputri",
                holderNik = testNik,
                validationToken = "tok_123",
            )

        val stringRep = ticket.toString()

        assertFalse(
            stringRep.contains(testNik),
            "NIK should be redacted in BookingTicket.toString(), but got: $stringRep",
        )
        assertFalse(
            stringRep.contains(partialNik),
            "Partial NIK should not appear in toString(), but got: $stringRep",
        )
        assertTrue(
            stringRep.contains("<redacted>"),
            "BookingTicket.toString() should show <redacted>, got: $stringRep",
        )
    }

    @Test
    fun bookingRequestDoesNotLeakNikFromTickets() {
        val request =
            BookingRequest(
                eventId = "evt_1",
                tickets =
                    listOf(
                        BookingTicket("cat_1", "Nadia", testNik, "tok_1"),
                        BookingTicket("cat_2", "Raka", "3201123456780001", "tok_2"),
                    ),
            )

        val stringRep = request.toString()

        assertFalse(
            stringRep.contains(testNik),
            "NIK should not appear in BookingRequest.toString(), but got: $stringRep",
        )
        assertFalse(
            stringRep.contains("3201123456780001"),
            "Second NIK should not appear in BookingRequest.toString(), but got: $stringRep",
        )
    }
}
