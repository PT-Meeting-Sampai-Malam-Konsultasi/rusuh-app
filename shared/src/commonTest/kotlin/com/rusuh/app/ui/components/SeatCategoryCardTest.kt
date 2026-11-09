package com.rusuh.app.ui.components

import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.seat_category_almost_gone
import rusuh_app.shared.generated.resources.seat_category_available
import rusuh_app.shared.generated.resources.seat_category_sold_out
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeatCategoryCardTest {
    private fun sample(availability: SeatAvailability) =
        SeatCategoryUi(
            id = "fest-b",
            name = "Festival B",
            description = "Berdiri · di belakang Festival A",
            priceRupiah = 850_000,
            availability = availability,
        )

    @Test
    fun priceFormatsWithIndonesianThousandsDots() {
        assertEquals("Rp 850.000", formatRupiah(850_000))
        assertEquals("Rp 1.250.000", formatRupiah(1_250_000))
        assertEquals("Rp 0", formatRupiah(0))
    }

    @Test
    fun availabilityMapsToItsLabelResource() {
        assertEquals(Res.string.seat_category_available, availabilityLabel(SeatAvailability.Available))
        assertEquals(Res.string.seat_category_almost_gone, availabilityLabel(SeatAvailability.AlmostGone))
        assertEquals(Res.string.seat_category_sold_out, availabilityLabel(SeatAvailability.SoldOut))
    }

    @Test
    fun isSoldOutOnlyForSoldOutAvailability() {
        assertTrue(sample(SeatAvailability.SoldOut).isSoldOut)
        assertFalse(sample(SeatAvailability.AlmostGone).isSoldOut)
        assertFalse(sample(SeatAvailability.Available).isSoldOut)
    }

    @Test
    fun rejectsBlankIdNameAndNegativePrice() {
        assertFailsWith<IllegalArgumentException> { SeatCategoryUi(" ", "A", null, 1) }
        assertFailsWith<IllegalArgumentException> { SeatCategoryUi("a", " ", null, 1) }
        assertFailsWith<IllegalArgumentException> { SeatCategoryUi("a", "A", null, -1) }
    }
}
