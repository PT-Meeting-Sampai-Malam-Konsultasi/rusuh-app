package com.rusuh.app.feature.detail

import com.rusuh.app.ui.components.SeatAvailability
import com.rusuh.app.ui.components.SeatCategoryUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetailScreenTest {
    private val categories =
        listOf(
            SeatCategoryUi("fest-b", "Festival B", null, 850_000, SeatAvailability.Available),
            SeatCategoryUi("tribun-atas", "Tribun Atas", null, 450_000, SeatAvailability.Available),
        )
    private val nikA = "3174055203960004"
    private val nikB = "3201123456780001"

    private fun content(reserved: Int = 3) =
        DetailContent(
            "E",
            null,
            "m",
            categories,
            "Nadia Saputri",
            "n@x.id",
            adminFeeRupiah = 10_000,
            reservedUnits = reserved,
        )

    private fun filled(vararg niks: String): DetailFormState {
        var form = DetailFormState().withQuantity("fest-b", niks.size, MAX_TICKETS_PER_ORDER)
        niks.forEachIndexed { i, nik -> form = form.withName(i, "Name $i").withNik(i, nik) }
        return form
    }

    @Test
    fun capReachedAtMax() {
        assertFalse(isCapReached(2))
        assertTrue(isCapReached(MAX_TICKETS_PER_ORDER))
        assertTrue(isCapReached(2, maxTickets = 2))
    }

    @Test
    fun maxTicketsIsCappedByReservedUnits() {
        assertEquals(3, content(reserved = 5).maxTickets)
        assertEquals(2, content(reserved = 2).maxTickets)
    }

    @Test
    fun quantityIsClampedToTheOrderCapAcrossCategories() {
        val form =
            DetailFormState()
                .withQuantity("fest-b", 2, 3)
                .withQuantity("tribun-atas", 5, 3)
        assertEquals(1, form.quantities["tribun-atas"])
        assertEquals(3, form.totalTickets)
        assertEquals(3, form.attendees.size)
    }

    @Test
    fun quantityRespectsReservedUnitsBelowThree() {
        assertEquals(2, DetailFormState().withQuantity("fest-b", 3, 2).totalTickets)
    }

    @Test
    fun attendeeSlotsGrowAndShrinkKeepingTypedValues() {
        var form = DetailFormState().withQuantity("fest-b", 2, 3).withNik(0, nikA)
        form = form.withQuantity("fest-b", 1, 3)
        assertEquals(1, form.attendees.size)
        assertEquals(nikA, form.attendees[0].nik)
        form = form.withQuantity("fest-b", 3, 3)
        assertEquals(nikA, form.attendees[0].nik)
        assertEquals("", form.attendees[2].nik)
    }

    @Test
    fun selfPrefillsOnlyTheNameNeverTheNik() {
        val form =
            DetailFormState()
                .withQuantity("fest-b", 1, 3)
                .withKind(0, AttendeeKind.Self, "Nadia Saputri")
        assertEquals(AttendeeKind.Self, form.attendees[0].kind)
        assertEquals("Nadia Saputri", form.attendees[0].name)
        assertEquals("", form.attendees[0].nik)
    }

    @Test
    fun switchingBackToOtherClearsOnlyAnAutofilledName() {
        val self = DetailFormState().withQuantity("fest-b", 1, 3).withKind(0, AttendeeKind.Self, "Nadia")
        assertEquals("", self.withKind(0, AttendeeKind.Other, "Nadia").attendees[0].name)
        val edited = self.withName(0, "Raka")
        assertEquals("Raka", edited.withKind(0, AttendeeKind.Other, "Nadia").attendees[0].name)
    }

    @Test
    fun nikInputKeepsDigitsOnlyCappedAtSixteen() {
        val form = DetailFormState().withQuantity("fest-b", 1, 3).withNik(0, "3174 0552-0396 0004 99")
        assertEquals("3174055203960004", form.attendees[0].nik)
    }

    @Test
    fun nikValidOnlyForSixteenDigits() {
        assertTrue(isNikValid(nikB))
        assertFalse(isNikValid("320112345678000"))
        assertFalse(isNikValid("32011234567800012"))
        assertFalse(isNikValid("3201 1234 5678 0001"))
        assertFalse(isNikValid(""))
    }

    @Test
    fun duplicateNikPointsAtTheEarlierTicket() {
        val form = filled(nikA, nikA)
        assertNull(form.duplicateOf(0))
        assertEquals(1, form.duplicateOf(1))
    }

    @Test
    fun incompleteNikIsNeverADuplicate() {
        val form = DetailFormState().withQuantity("fest-b", 2, 3).withNik(0, "3174").withNik(1, "3174")
        assertNull(form.duplicateOf(1))
    }

    @Test
    fun cannotProceedWithNoSelection() {
        assertFalse(canProceed(DetailFormState(), 3))
    }

    @Test
    fun cannotProceedWithBlankNameInvalidOrDuplicateNik() {
        assertFalse(canProceed(DetailFormState().withQuantity("fest-b", 1, 3).withNik(0, nikA), 3)) // no name
        assertFalse(canProceed(filled("123"), 3))
        assertFalse(canProceed(filled(nikA, nikA), 3))
    }

    @Test
    fun cannotProceedPastTheCap() {
        assertFalse(canProceed(filled(nikA, nikB), maxTickets = 1))
    }

    @Test
    fun canProceedWithNameAndDistinctValidNikPerTicket() {
        assertTrue(canProceed(filled(nikA, nikB), 3))
    }

    @Test
    fun ticketCategoriesExpandInListOrder() {
        val tickets = ticketCategories(categories, mapOf("tribun-atas" to 1, "fest-b" to 2))
        assertEquals(listOf("fest-b", "fest-b", "tribun-atas"), tickets.map { it.id })
    }

    @Test
    fun subtotalSumsSelectedCategories() {
        assertEquals(2 * 850_000 + 450_000, subtotalRupiah(categories, mapOf("fest-b" to 2, "tribun-atas" to 1)))
        assertEquals(0, subtotalRupiah(categories, emptyMap()))
    }

    @Test
    fun adminFeeIsChargedOncePerOrderNotPerTicket() {
        assertEquals(2_160_000, orderTotalRupiah(subtotal = 2_150_000, adminFee = 10_000, tickets = 3))
        assertEquals(860_000, orderTotalRupiah(subtotal = 850_000, adminFee = 10_000, tickets = 1))
        assertEquals(0, orderTotalRupiah(subtotal = 0, adminFee = 10_000, tickets = 0))
    }

    @Test
    fun countdownFormatsMinutesAndPaddedSeconds() {
        assertEquals("5:00", formatCountdown(ORDER_WINDOW_SECONDS))
        assertEquals("4:12", formatCountdown(252))
        assertEquals("0:05", formatCountdown(5))
        assertEquals("0:00", formatCountdown(-3))
    }

    @Test
    fun nikIsGroupedByFour() {
        assertEquals("3174 0552 0396 0004", groupNik(nikA))
        assertEquals("3174 05", groupNik("317405"))
        assertEquals("", groupNik(""))
    }
}
