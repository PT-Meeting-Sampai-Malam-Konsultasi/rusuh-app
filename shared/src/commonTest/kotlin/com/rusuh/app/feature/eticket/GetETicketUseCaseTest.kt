package com.rusuh.app.feature.eticket

import com.rusuh.app.feature.tickets.StubTicketsRepository
import com.rusuh.app.feature.tickets.TicketOrder
import com.rusuh.app.feature.tickets.TicketsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class GetETicketUseCaseTest {
    private val useCase = GetETicketUseCase(StubTicketsRepository())

    @Test
    fun samudraOrderHasThreeTicketsWithTwoCategories() =
        runTest {
            val found = assertIs<ETicketResult.Found>(useCase("RSH-26-084731"))
            assertEquals(3, found.order.tickets.size)
            assertEquals(listOf("Festival B", "Festival B", "Tribun Atas"), found.order.tickets.map { it.categoryName })
            assertEquals(
                listOf(Seating.Standing, Seating.Standing, Seating.Seated),
                found.order.tickets.map { it.seating },
            )
            assertEquals("SAMUDRA", found.order.eventName)
            assertEquals(EventZone.Wib, found.order.zone)
        }

    @Test
    fun everyStubOrderHasTickets() =
        runTest {
            val repository = StubTicketsRepository()
            repository.getMyOrders().forEach { order ->
                val found = assertIs<ETicketResult.Found>(useCase(order.orderId), "order ${order.orderId}")
                assertEquals(order.attendees.size, found.order.tickets.size)
            }
        }

    @Test
    fun pastWitaOrderKeepsItsZone() =
        runTest {
            val found = assertIs<ETicketResult.Found>(useCase("RSH-26-052118"))
            assertEquals(EventZone.Wita, found.order.zone)
        }

    @Test
    fun unknownOrderIsNotFound() =
        runTest {
            assertEquals(ETicketResult.NotFound, useCase("RSH-NOPE"))
        }

    @Test
    fun blankOrderIdIsNotFoundWithoutHittingTheRepository() =
        runTest {
            assertEquals(ETicketResult.NotFound, GetETicketUseCase(ExplodingRepository)("  "))
        }

    @Test
    fun orderWithoutTicketsIsNotFound() =
        runTest {
            val json =
                """{"orders":[{"orderId":"X","eventTitle":"x","dateVenue":"","totalLabel":"","dayLabel":"1",
                "monthLabel":"OKT","startsAtEpochMillis":1}]}"""
            assertEquals(ETicketResult.NotFound, GetETicketUseCase(StubTicketsRepository(json))("X"))
        }

    @Test
    fun loadUiStateMapsFoundToContent() =
        runTest {
            val ui = assertIs<ETicketUiState.Content>(useCase.loadUiState("RSH-26-084731"))
            assertEquals(3, ui.ui.tickets.size)
        }

    @Test
    fun loadUiStateMapsNotFound() =
        runTest {
            assertEquals(ETicketUiState.NotFound, useCase.loadUiState("RSH-NOPE"))
        }

    @Test
    fun loadUiStateMapsAFailureToError() =
        runTest {
            assertEquals(ETicketUiState.Error, GetETicketUseCase(ExplodingRepository).loadUiState("RSH-1"))
        }

    @Test
    fun loadUiStateRethrowsCancellation() =
        runTest {
            assertFailsWith<CancellationException> { GetETicketUseCase(CancellingRepository).loadUiState("RSH-1") }
        }

    private object ExplodingRepository : TicketsRepository {
        override suspend fun getMyOrders(): List<TicketOrder> = error("boom")

        override suspend fun getOrderTickets(orderId: String): ETicketOrder? = error("boom")
    }

    private object CancellingRepository : TicketsRepository {
        override suspend fun getMyOrders(): List<TicketOrder> = emptyList()

        override suspend fun getOrderTickets(orderId: String): ETicketOrder? = throw CancellationException("cancelled")
    }
}
