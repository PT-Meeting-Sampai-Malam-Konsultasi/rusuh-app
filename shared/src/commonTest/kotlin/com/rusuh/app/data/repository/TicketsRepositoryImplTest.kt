package com.rusuh.app.data.repository

import com.rusuh.app.data.cache.TicketsCache
import com.rusuh.app.data.network.AppLanguage
import com.rusuh.app.data.remote.TicketApiService
import com.rusuh.app.data.storage.AppPreferences
import com.rusuh.app.data.storage.FakeAppPreferences
import com.rusuh.app.domain.model.DomainException
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.feature.auth.FakeAccountEmails
import com.rusuh.app.feature.eticket.EventZone
import com.rusuh.app.feature.eticket.Seating
import com.rusuh.app.feature.tickets.PosterTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TicketsRepositoryImplTest {
    private fun repository(
        backend: FakeBackend,
        language: AppLanguage = AppLanguage.Indonesian,
        now: Long = FIXTURE_NOW_MILLIS,
        cache: TicketsCache = TicketsCache(FakeAppPreferences()),
        emails: FakeAccountEmails = FakeAccountEmails(),
    ) = TicketsRepositoryImpl(
        TicketApiService(backend.api),
        cache,
        { language },
        TestServerClock(now),
        emails,
    )

    private fun backendWith(vararg replies: Pair<String, Reply>): FakeBackend =
        FakeBackend().also { replies.forEach { (route, reply) -> it.on(route, reply) } }

    private fun ticketsBackend(ticketsReply: Reply): FakeBackend =
        backendWith(
            "GET /core/tickets" to ticketsReply,
            "GET /core/events/evt_2026_gbk" to Reply.ok(EVENT_GBK_JSON),
        )

    @Test
    fun getMyOrdersGroupsTicketsByBookingIdIntoOrders() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_TWO_ORDERS_JSON))
            val orders = repository(backend).getMyOrders()

            assertEquals(2, orders.size)
            val order1 = orders.find { it.orderId == "bk_01JB2Y6Z8Q" }
            val order2 = orders.find { it.orderId == "bk_01JB3ABC12" }
            assertNotNull(order1)
            assertNotNull(order2)
            assertEquals(2, order1.attendees.size)
            assertEquals(1, order2.attendees.size)
        }

    @Test
    fun getMyOrdersPopulatesEventFactsFromEventEndpoint() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_JSON))
            val orders = repository(backend).getMyOrders()

            val order = orders.single()
            assertEquals("Samudra", order.eventTitle)
            assertEquals("SAMUDRA", order.posterLabel)
            assertTrue(order.dateVenue.contains("Sab, 12 Des 2026"))
            assertTrue(order.dateVenue.contains("GBK"))
        }

    @Test
    fun getMyOrdersSortsUpcomingEventsWithCountdown() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_JSON))
            val orders = repository(backend).getMyOrders()

            val order = orders.single()
            // Event is in the future (Dec 2026), now is Nov 2026
            assertEquals(false, order.isPast)
            assertNotNull(order.countdownLabel)
            assertTrue(order.countdownLabel!!.contains("hari") || order.countdownLabel!!.contains("jam"))
        }

    @Test
    fun getMyOrdersMarksPastEvents() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_JSON))
            // Set now to after the event (2027)
            val orders = repository(backend, now = 1_800_000_000_000L).getMyOrders()

            val order = orders.single()
            assertTrue(order.isPast)
            assertNull(order.countdownLabel)
        }

    @Test
    fun getMyOrdersHandlesMultipleTicketsPerOrder() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_JSON))
            val orders = repository(backend).getMyOrders()

            val order = orders.single()
            assertEquals(2, order.attendees.size)
            assertEquals("Festival B", order.attendees[0].category)
            assertEquals("Rina Wulandari", order.attendees[0].holderName)
            assertEquals("Festival B", order.attendees[1].category)
            assertEquals("Dimas Pratama", order.attendees[1].holderName)
        }

    @Test
    fun getMyOrdersAssignsStablePosterToneFromEventId() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_JSON))
            val orders1 = repository(backend).getMyOrders()
            val orders2 = repository(backend).getMyOrders()

            assertEquals(orders1.single().posterTone, orders2.single().posterTone)
            assertTrue(orders1.single().posterTone in PosterTone.entries)
        }

    @Test
    fun getMyOrdersHandlesMissingEventFacts() =
        runTest {
            // No event endpoint configured - will return 404
            val backend = backendWith("GET /core/tickets" to Reply.ok(TICKETS_JSON))
            val orders = repository(backend).getMyOrders()

            val order = orders.single()
            // Falls back to eventId when event fetch fails
            assertEquals("evt_2026_gbk", order.eventTitle)
        }

    @Test
    fun getMyOrdersReturnsEmptyListForEmptyTickets() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_EMPTY_JSON))
            val orders = repository(backend).getMyOrders()

            assertTrue(orders.isEmpty())
        }

    @Test
    fun getMyOrdersThrowsDomainExceptionOn401() =
        runTest {
            val backend = backendWith("GET /core/tickets" to Reply.problem(401, "UNAUTHORIZED"))
            val failure = assertFailsWith<DomainException> { repository(backend).getMyOrders() }

            assertEquals(401, assertIs<DomainResult.Error>(failure.result).code)
        }

    @Test
    fun getMyOrdersThrowsDomainExceptionOnNetworkError() =
        runTest {
            val backend = backendWith("GET /core/tickets" to Reply.offline())
            val failure = assertFailsWith<DomainException> { repository(backend).getMyOrders() }

            assertEquals(DomainResult.NetworkError, failure.result)
        }

    @Test
    fun getMyOrdersRethrowsCancellationFromEventFactsCache() =
        runTest {
            val preferences =
                object : AppPreferences {
                    override suspend fun get(key: String): String? = throw CancellationException("cancelled")

                    override suspend fun set(
                        key: String,
                        value: String,
                    ) = Unit

                    override suspend fun remove(key: String) = Unit
                }
            val backend = backendWith("GET /core/tickets" to Reply.ok(TICKETS_JSON))

            assertFailsWith<CancellationException> {
                repository(backend, cache = TicketsCache(preferences)).getMyOrders()
            }
        }

    @Test
    fun getOrderTicketsReturnsETicketOrderForValidOrderId() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_JSON))
            val eticket = repository(backend).getOrderTickets("bk_01JB2Y6Z8Q")

            assertNotNull(eticket)
            assertEquals("bk_01JB2Y6Z8Q", eticket.orderId)
            assertEquals("Samudra", eticket.eventName)
            assertEquals("GBK", eticket.venueShort)
            assertEquals(EventZone.Wib, eticket.zone)
            assertEquals(2, eticket.tickets.size)
        }

    @Test
    fun getOrderTicketsReturnsTicketDetailsWithQrPayload() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_JSON))
            val eticket = repository(backend).getOrderTickets("bk_01JB2Y6Z8Q")!!

            val ticket = eticket.tickets.first()
            assertEquals("tk_01JB2YBA1", ticket.ticketId)
            assertEquals("SMD-FB-04827", ticket.ticketCode)
            assertEquals("Festival B", ticket.categoryName)
            assertEquals(Seating.Standing, ticket.seating)
            assertEquals("Gerbang 5", ticket.entryGate)
            assertEquals("Rina Wulandari", ticket.holderName)
            assertEquals("3174 •••• •••• 0004", ticket.nikMasked)
            assertEquals("RSH1.SMD-FB-04827.Q2hlY2tJbi1zaWduZWQ", ticket.qrPayload)
        }

    @Test
    fun getOrderTicketsReturnsNullForUnknownOrderId() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_JSON))
            val eticket = repository(backend).getOrderTickets("unknown_order")

            assertNull(eticket)
        }

    @Test
    fun getOrderTicketsReturnsNullForBlankOrderId() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_JSON))
            val eticket = repository(backend).getOrderTickets("")

            assertNull(eticket)
        }

    @Test
    fun cachesEventFactsAcrossMultipleCalls() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_JSON))
            val repo = repository(backend)

            repo.getMyOrders()
            repo.getMyOrders()

            // Event endpoint should be called only once due to caching
            assertEquals(1, backend.callsTo("GET /core/events/evt_2026_gbk"))
        }

    @Test
    fun englishLanguageUsesEnglishFormatting() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_JSON))
            val orders = repository(backend, language = AppLanguage.English).getMyOrders()

            val order = orders.single()
            assertTrue(order.dateVenue.contains("Sat, 12 Dec 2026") || order.dateVenue.contains("Dec"))
        }

    private companion object {
        /** Single ticket. */
        const val TICKET_1_JSON =
            """{"ticketId":"tk_01JB2YBA1","ticketCode":"SMD-FB-04827","bookingId":"bk_01JB2Y6Z8Q",
               "eventId":"evt_2026_gbk","categoryId":"cat_festival_b","categoryName":"Festival B",
               "seating":"standing","entryGate":"Gerbang 5","holderName":"Rina Wulandari",
               "nikMasked":"3174 •••• •••• 0004","qrPayload":"RSH1.SMD-FB-04827.Q2hlY2tJbi1zaWduZWQ",
               "issuedAt":"2026-11-14T10:46:00Z"}"""

        const val TICKET_2_JSON =
            """{"ticketId":"tk_01JB2YBA2","ticketCode":"SMD-FB-04828","bookingId":"bk_01JB2Y6Z8Q",
               "eventId":"evt_2026_gbk","categoryId":"cat_festival_b","categoryName":"Festival B",
               "seating":"standing","entryGate":"Gerbang 5","holderName":"Dimas Pratama",
               "nikMasked":"3275 •••• •••• 0019","qrPayload":"RSH1.SMD-FB-04828.Q2hlY2tJbi1zaWduZWQ",
               "issuedAt":"2026-11-14T10:46:00Z"}"""

        const val TICKET_3_JSON =
            """{"ticketId":"tk_01JB3XYZ99","ticketCode":"SMD-VIP-00001","bookingId":"bk_01JB3ABC12",
               "eventId":"evt_2026_gbk","categoryId":"cat_vip","categoryName":"VIP",
               "seating":"seated","entryGate":"Gerbang 1","holderName":"Ahmad Syafiq",
               "nikMasked":"3201 •••• •••• 0042","qrPayload":"RSH1.SMD-VIP-00001.YW5vdGhlci1zaWduZWQ",
               "issuedAt":"2026-11-14T11:00:00Z"}"""

        /** Two tickets in one order. */
        const val TICKETS_JSON = """{"items":[$TICKET_1_JSON,$TICKET_2_JSON]}"""

        /** Tickets across two different orders. */
        const val TICKETS_TWO_ORDERS_JSON = """{"items":[$TICKET_1_JSON,$TICKET_2_JSON,$TICKET_3_JSON]}"""

        /** Empty ticket list. */
        const val TICKETS_EMPTY_JSON = """{"items":[]}"""

        /** Event details for evt_2026_gbk. */
        const val EVENT_GBK_JSON =
            """{"eventId":"evt_2026_gbk","name":"Samudra","subtitle":"Tur Gelombang 2026","format":"Tour",
               "venue":"Stadion Utama GBK","venueShort":"GBK","city":"Jakarta","timezone":"Asia/Jakarta",
               "startsAt":"2026-12-12T12:00:00Z","doorsOpenAt":"2026-12-12T10:00:00Z",
               "saleOpensAt":"2026-11-14T03:00:00Z","waitingRoomOpensAt":"2026-11-14T02:30:00Z",
               "saleEndsAt":"2026-11-20T16:59:59Z","state":"on_sale",
               "priceFrom":{"amount":750000,"currency":"IDR"},"capacity":12000,"soldOutAt":null,
               "imageUrl":"https://cdn.rusuh.id/events/evt_2026_gbk/hero.jpg"}"""
    }

    @Test
    fun eticketOrderCarriesTheAccountEmailSavedAtSignIn() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_JSON))
            val emails = FakeAccountEmails().apply { stored = "saved@example.test" }
            val orderId = repository(backend, emails = emails).getMyOrders().single().orderId

            assertEquals(
                "saved@example.test",
                repository(backend, emails = emails).getOrderTickets(orderId)?.contactEmail,
            )
        }

    @Test
    fun eticketOrderHasNoEmailWhenNoneIsSaved() =
        runTest {
            val backend = ticketsBackend(Reply.ok(TICKETS_JSON))
            val orderId = repository(backend).getMyOrders().single().orderId

            assertEquals("", repository(backend).getOrderTickets(orderId)?.contactEmail)
        }
}
