package com.rusuh.app.feature.eticket

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ETicketMapperTest {
    private val samudraDoors = 1_797_066_000_000L // 2026-12-12 09:00 UTC

    @Test
    fun doorsOpenInWibIsSaturday1200Dec16h() {
        val moment = momentOf(samudraDoors, EventZone.Wib)
        assertEquals(
            EventMoment(weekdayIndex = 5, day = 12, monthIndex = 11, hour = 16, minute = 0, zoneLabel = "WIB"),
            moment,
        )
    }

    @Test
    fun sameInstantInWitaIsOneHourLater() {
        val moment = momentOf(samudraDoors, EventZone.Wita)
        assertEquals(17, moment.hour)
        assertEquals("WITA", moment.zoneLabel)
        assertEquals(12, moment.day)
    }

    @Test
    fun sameInstantInWitIsTwoHoursLater() {
        val moment = momentOf(samudraDoors, EventZone.Wit)
        assertEquals(18, moment.hour)
        assertEquals("WIT", moment.zoneLabel)
    }

    @Test
    fun paidTimeKeepsMinutes() {
        val moment = momentOf(1_794_200_820_000L, EventZone.Wib) // 2026-11-09 05:07 UTC
        assertEquals("12", moment.hourText())
        assertEquals("07", moment.minuteText())
        assertEquals(0, moment.weekdayIndex) // Monday
        assertEquals(10, moment.monthIndex)
    }

    @Test
    fun localTimePastMidnightRollsToTheNextYear() {
        val moment = momentOf(1_798_740_000_000L, EventZone.Wib) // 2026-12-31 18:00 UTC
        assertEquals(1, moment.day)
        assertEquals(0, moment.monthIndex)
        assertEquals(4, moment.weekdayIndex) // Friday 2027-01-01
        assertEquals("01", moment.hourText())
    }

    @Test
    fun leapDayAndMonthRolloverAreHandled() {
        val leap = momentOf(1_835_438_400_000L, EventZone.Wib) // 2028-02-29 12:00 UTC
        assertEquals(29, leap.day)
        assertEquals(1, leap.monthIndex)
        assertEquals(1, leap.weekdayIndex) // Tuesday
        val rolled = momentOf(1_835_467_200_000L, EventZone.Wit) // 2028-02-29 20:00 UTC is 05:00 on 1 March WIT
        assertEquals(1, rolled.day)
        assertEquals(2, rolled.monthIndex)
        assertEquals(2, rolled.weekdayIndex) // Wednesday
    }

    @Test
    fun instantsBeforeTheEpochFloorCorrectly() {
        val moment = momentOf(-1L, EventZone.Wib) // 1969-12-31 23:59:59.999 UTC
        assertEquals(1, moment.day)
        assertEquals(0, moment.monthIndex)
        assertEquals(3, moment.weekdayIndex) // Thursday 1970-01-01
        assertEquals("06", moment.hourText())
        assertEquals("59", moment.minuteText())
    }

    @Test
    fun zoneLabelsParseCaseInsensitivelyAndDefaultToWib() {
        assertEquals(EventZone.Wita, EventZone.fromLabel("wita"))
        assertEquals(EventZone.Wit, EventZone.fromLabel("WIT"))
        assertEquals(EventZone.Wib, EventZone.fromLabel("elsewhere"))
    }

    @Test
    fun seatingParsesTheApiValues() {
        assertEquals(Seating.Standing, Seating.fromApi("standing"))
        assertEquals(Seating.Seated, Seating.fromApi("SEATED"))
        assertEquals(Seating.Unknown, Seating.fromApi("balcony"))
        assertEquals(Seating.Unknown, Seating.fromApi(null))
    }

    @Test
    fun entryTextJoinsGateAndSeatingAndSkipsBlanks() {
        assertEquals("Gerbang 5 · berdiri", entryText("Gerbang 5", "berdiri", " · "))
        assertEquals("Gerbang 9, duduk bebas", entryText("Gerbang 9", "duduk bebas", ", "))
        assertEquals("berdiri", entryText("  ", "berdiri", " · "))
        assertEquals("Gerbang 5", entryText("Gerbang 5", null, " · "))
        assertEquals("", entryText("", null, " · "))
    }

    @Test
    fun boldRangeFindsTheFirstOccurrence() {
        assertEquals(10 until 13, boldRange("Order no. ABC and ABC", "ABC"))
        assertNull(boldRange("Order", "XYZ"))
        assertNull(boldRange("Order", ""))
    }

    @Test
    fun cardsAreNumberedFromOneOfTotal() {
        val ui = sampleETicketOrder(3).toUi()
        assertEquals(listOf(1, 2, 3), ui.tickets.map { it.position })
        assertEquals(listOf(3, 3, 3), ui.tickets.map { it.total })
    }

    @Test
    fun singleTicketIsOneOfOne() {
        val card = sampleETicketOrder(1).toUi().tickets.single()
        assertEquals(1, card.position)
        assertEquals(1, card.total)
    }

    @Test
    fun maskedNikPassesThroughUntouched() {
        val ui = sampleETicketOrder(3).toUi()
        assertEquals(
            listOf("3174 •••• •••• 0004", "3201 •••• •••• 0017", "3273 •••• •••• 0002"),
            ui.tickets.map {
                it.nikMasked
            },
        )
    }

    @Test
    fun qrPayloadAndCodeAreCarriedExactly() {
        val card = sampleETicketOrder(1).toUi().tickets.single()
        assertEquals("RSH1.SMD-FB-04827.Q2hlY2tJbi1zaWduZWQ", card.qrPayload)
        assertEquals("SMD-FB-04827", card.ticketCode)
    }

    @Test
    fun categoriesAlternateBlueThenPinkInFirstSeenOrder() {
        val tones = sampleETicketOrder(3).toUi().tickets.map { it.tone }
        assertEquals(listOf(TicketTone.Blue, TicketTone.Blue, TicketTone.Pink), tones)
    }

    @Test
    fun everyCardCarriesTheEventNameAndDoorsOpenMoment() {
        val ui = sampleETicketOrder(3).toUi()
        assertEquals(listOf("SAMUDRA", "SAMUDRA", "SAMUDRA"), ui.tickets.map { it.eventName })
        assertEquals(16, ui.tickets.first().doorsOpen.hour)
        assertEquals(12, ui.paidAt.hour)
    }

    @Test
    fun orderHeaderFactsArePassedThrough() {
        val ui = sampleETicketOrder(3).toUi()
        assertEquals("RSH-26-084731", ui.orderId)
        assertEquals("Rp 2.180.000", ui.orderTotal)
        assertEquals("QRIS", ui.paymentMethod)
        assertEquals("GBK", ui.venueShort)
        assertEquals("nadia.saputri@example.com", ui.contactEmail)
    }
}
