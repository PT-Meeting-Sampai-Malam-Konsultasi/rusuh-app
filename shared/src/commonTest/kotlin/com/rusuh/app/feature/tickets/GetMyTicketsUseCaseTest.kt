package com.rusuh.app.feature.tickets

import com.rusuh.app.feature.eticket.ETicketOrder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetMyTicketsUseCaseTest {
    @Test
    fun guestGetsSignedOutWithoutHittingTheRepository() =
        runTest {
            val useCase = GetMyTicketsUseCase(FailingRepository, isSignedIn = { false })
            assertEquals(MyTickets.SignedOut, useCase())
        }

    @Test
    fun theSessionIsAskedOnEveryCallSoALoginOrLogoutShowsUpAtOnce() =
        runTest {
            var signedIn = false
            val useCase = GetMyTicketsUseCase(StubTicketsRepository(), isSignedIn = { signedIn })

            assertEquals(MyTickets.SignedOut, useCase())
            signedIn = true
            assertIs<MyTickets.Content>(useCase())
            signedIn = false
            assertEquals(MyTickets.SignedOut, useCase())
        }

    @Test
    fun noOrdersIsEmpty() =
        runTest {
            val useCase = GetMyTicketsUseCase(StubTicketsRepository(StubTicketsJson.EMPTY))
            assertEquals(MyTickets.Empty, useCase())
        }

    @Test
    fun stubOrdersSplitIntoUpcomingSoonestFirstAndPast() =
        runTest {
            val result = assertIs<MyTickets.Content>(GetMyTicketsUseCase(StubTicketsRepository())())
            assertEquals(listOf("RSH-26-071205", "RSH-26-084731"), result.upcoming.map { it.orderId })
            assertEquals(listOf("RSH-26-052118"), result.past.map { it.orderId })
        }

    @Test
    fun stubParsesAttendeesAndOptionalFields() =
        runTest {
            val order = StubTicketsRepository().getMyOrders().first { it.orderId == "RSH-26-084731" }
            assertEquals(3, order.attendees.size)
            assertEquals("Tribun Atas", order.attendees.last().category)
            assertEquals("33 hari lagi", order.countdownLabel)
        }

    @Test
    fun mapperKeepsPastOrdersGreyAndMarkedPast() =
        runTest {
            val content = assertIs<MyTickets.Content>(GetMyTicketsUseCase(StubTicketsRepository())())
            val ui = assertIs<TicketsUiState.Content>(content.toUiState())
            assertEquals(true, ui.past.single().isPast)
            assertEquals(5, ui.upcoming.sumOf { it.attendees.size })
        }

    @Test
    fun upcomingOrdersAreSortedBySoonestStart() =
        runTest {
            val result = assertIs<MyTickets.Content>(GetMyTicketsUseCase(StubTicketsRepository())())
            val starts = result.upcoming.map { it.startsAtEpochMillis }
            assertEquals(starts.sorted(), starts)
            // Soonest first: 4 Des (Pelita) precedes 12 Des (Samudra).
            assertEquals("4", result.upcoming.first().dayLabel)
        }

    @Test
    fun pastOrdersAreSortedLatestFirst() =
        runTest {
            val json =
                """{"orders":[
                {"orderId":"A","eventTitle":"a","dateVenue":"","totalLabel":"","dayLabel":"1",
                "monthLabel":"OKT","startsAtEpochMillis":100,"past":true},
                {"orderId":"B","eventTitle":"b","dateVenue":"","totalLabel":"","dayLabel":"2",
                "monthLabel":"NOV","startsAtEpochMillis":200,"past":true}]}"""
            val result = assertIs<MyTickets.Content>(GetMyTicketsUseCase(StubTicketsRepository(json))())
            assertEquals(listOf("B", "A"), result.past.map { it.orderId })
            assertEquals(emptyList(), result.upcoming)
        }

    @Test
    fun rowsWithBlankOrderIdAreDropped() =
        runTest {
            val json =
                """{"orders":[{"orderId":" ","eventTitle":"x","dateVenue":"","totalLabel":"",
                "dayLabel":"1","monthLabel":"OKT","startsAtEpochMillis":1}]}"""
            assertEquals(MyTickets.Empty, GetMyTicketsUseCase(StubTicketsRepository(json))())
        }

    @Test
    fun malformedJsonBecomesErrorState() =
        runTest {
            val state = GetMyTicketsUseCase(StubTicketsRepository("not json")).loadUiState()
            assertEquals(TicketsUiState.Error, state)
        }

    @Test
    fun repositoryFailureBecomesErrorState() =
        runTest {
            assertEquals(TicketsUiState.Error, GetMyTicketsUseCase(FailingRepository).loadUiState())
        }

    @Test
    fun emptyAndSignedOutMapToTheirUiStates() {
        assertEquals(TicketsUiState.Empty, MyTickets.Empty.toUiState())
        assertEquals(TicketsUiState.SignedOut, MyTickets.SignedOut.toUiState())
    }

    @Test
    fun pastOrderIsForcedGreyEvenWhenToneIsBlue() {
        val order =
            TicketOrder("P", "t", "d", "L", PosterTone.Blue, emptyList(), "", "1", "OKT", 1L, isPast = true)
        assertEquals(com.rusuh.app.ui.components.TicketPoster.Grey, order.toUi().poster)
    }

    private object FailingRepository : TicketsRepository {
        override suspend fun getMyOrders(): List<TicketOrder> = error("must not be called")

        override suspend fun getOrderTickets(orderId: String): ETicketOrder? = error("must not be called")
    }
}
