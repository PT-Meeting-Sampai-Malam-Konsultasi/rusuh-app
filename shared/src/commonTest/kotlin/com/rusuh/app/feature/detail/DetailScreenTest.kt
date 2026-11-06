package com.rusuh.app.feature.detail

import com.rusuh.app.ui.components.SeatCategoryUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetailScreenTest {
    private val categories =
        listOf(
            SeatCategoryUi("fest-b", "Festival B", null, priceRupiah = 850_000, available = 120),
            SeatCategoryUi("tribun-atas", "Tribun Atas", null, priceRupiah = 450_000, available = 300),
        )

    @Test
    fun capReachedAtThreeTickets() {
        assertFalse(isCapReached(0))
        assertFalse(isCapReached(2))
        assertTrue(isCapReached(MAX_TICKETS_PER_ORDER))
        assertTrue(isCapReached(MAX_TICKETS_PER_ORDER + 1))
    }

    @Test
    fun nikValidOnlyForSixteenDigits() {
        assertTrue(isNikValid("3201123456780001"))
        assertFalse(isNikValid("320112345678000")) // 15 digits
        assertFalse(isNikValid("32011234567800012")) // 17 digits
        assertFalse(isNikValid("3201 1234 5678 0001")) // spaces are not digits
        assertFalse(isNikValid(""))
    }

    @Test
    fun cannotProceedWithNoSelection() {
        assertFalse(canProceed(selectedTotal = 0, niks = emptyList()))
    }

    @Test
    fun cannotProceedWhenNikCountMismatchesSelection() {
        assertFalse(canProceed(selectedTotal = 2, niks = listOf("3201123456780001")))
    }

    @Test
    fun cannotProceedWithAnInvalidNik() {
        assertFalse(
            canProceed(
                selectedTotal = 2,
                niks = listOf("3201123456780001", "123"),
            ),
        )
    }

    @Test
    fun cannotProceedPastTheCap() {
        val fourValid = List(4) { "3201123456780001" }
        assertFalse(canProceed(selectedTotal = 4, niks = fourValid))
    }

    @Test
    fun canProceedWithValidNikPerTicketWithinCap() {
        assertTrue(
            canProceed(
                selectedTotal = 2,
                niks = listOf("3201123456780001", "3174055203960004"),
            ),
        )
    }

    @Test
    fun subtotalSumsSelectedCategories() {
        val quantities = mapOf("fest-b" to 2, "tribun-atas" to 1)
        assertEquals(2 * 850_000 + 450_000, subtotalRupiah(categories, quantities))
    }

    @Test
    fun subtotalIsZeroWithNoSelection() {
        assertEquals(0, subtotalRupiah(categories, emptyMap()))
    }
}
