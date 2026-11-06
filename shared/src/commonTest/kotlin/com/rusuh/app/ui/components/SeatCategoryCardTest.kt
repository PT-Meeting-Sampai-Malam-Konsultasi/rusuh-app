package com.rusuh.app.ui.components

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
    fun availabilityShowsRemainingCountWhenInStock() {
        assertEquals("Sisa 1.200 tiket", availabilityLabel(sample(1_200)))
    }

    @Test
    fun availabilityShowsSoldOutWhenZero() {
        assertEquals("Habis", availabilityLabel(sample(0)))
    }

    @Test
    fun isSoldOutOnlyWhenAvailabilityZero() {
        assertTrue(sample(0).isSoldOut)
        assertFalse(sample(1).isSoldOut)
    }

    @Test
    fun descriptionIncludesNamePriceAvailabilityAndQuantity() {
        val desc = buildSeatCategoryDescription(sample(50), quantity = 2)
        assertTrue(desc.contains("Festival B"), desc)
        assertTrue(desc.contains("Rp 850.000"), desc)
        assertTrue(desc.contains("Sisa 50 tiket"), desc)
        assertTrue(desc.contains("2 dipilih"), desc)
    }

    @Test
    fun descriptionOmitsQuantityWhenSoldOut() {
        val desc = buildSeatCategoryDescription(sample(0), quantity = 0)
        assertTrue(desc.contains("Habis"), desc)
        assertFalse(desc.contains("dipilih"), desc)
    }
}
