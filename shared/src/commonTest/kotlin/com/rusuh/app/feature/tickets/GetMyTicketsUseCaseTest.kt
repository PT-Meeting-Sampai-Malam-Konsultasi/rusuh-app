package com.rusuh.app.feature.tickets

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
    fun noOrdersIsEmpty() =
        runTest {
            val useCase = GetMyTicketsUseCase(StubTicketsRepository(StubTicketsJson.EMPTY))
            assertEquals(MyTickets.Empty, useCase())
        }

    @Test
    fun stubOrdersSplitIntoUpcomingSoonestFirstAndPast() =
        runTest {
            val result = assertIs<MyTickets.Content>(GetMyTicketsUseCase(StubTicketsRepository())())
            assertEquals(listOf("RSH-26-084731", "RSH-26-071205"), result.upcoming.map { it.orderId })
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

    private object FailingRepository : TicketsRepository {
        override suspend fun getMyOrders(): List<TicketOrder> = error("must not be called")
    }
}
