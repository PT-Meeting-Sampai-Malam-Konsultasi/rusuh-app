package com.rusuh.app.ui.components

import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.seat_remaining
import rusuh_app.shared.generated.resources.seat_sold_out
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeatCategoryCardTest {
    private fun sample(available: Int) =
        SeatCategoryUi(
            id = "fest-b",
            name = "Festival B",
            description = "Berdiri · di belakang Festival A",
            priceRupiah = 850_000,
            available = available,
        )

    @Test
    fun priceFormatsWithIndonesianThousandsDots() {
        assertEquals("Rp 850.000", formatRupiah(850_000))
        assertEquals("Rp 1.250.000", formatRupiah(1_250_000))
        assertEquals("Rp 0", formatRupiah(0))
    }

    @Test
    fun availabilityUsesRemainingKeyWithFormattedCountWhenInStock() {
        assertEquals(
            AvailabilityLabel(Res.string.seat_remaining, "1.200"),
            availabilityLabel(sample(1_200)),
        )
    }

    @Test
    fun availabilityUsesSoldOutKeyWhenZero() {
        assertEquals(AvailabilityLabel(Res.string.seat_sold_out), availabilityLabel(sample(0)))
    }

    @Test
    fun isSoldOutOnlyWhenAvailabilityZero() {
        assertTrue(sample(0).isSoldOut)
        assertFalse(sample(1).isSoldOut)
    }
}
