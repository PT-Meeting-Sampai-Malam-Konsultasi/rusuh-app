package com.rusuh.app.data.repository

import com.rusuh.app.data.network.AppLanguage
import com.rusuh.app.data.remote.EventApiService
import com.rusuh.app.domain.model.DomainException
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.feature.home.HomeEventItem
import com.rusuh.app.ui.components.EventFormat
import com.rusuh.app.ui.components.EventPosterTone
import com.rusuh.app.ui.components.EventStatus
import com.rusuh.app.ui.components.TimeZoneLabel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HomeRepositoryImplTest {
    private fun repository(
        backend: FakeBackend,
        language: AppLanguage = AppLanguage.Indonesian,
        now: Long = 0L,
    ) = HomeRepositoryImpl(EventApiService(backend.api), { language }, TestServerClock(now))

    private fun backendWith(vararg replies: Reply) = FakeBackend().also { it.on("GET /core/events", *replies) }

    private suspend fun events(language: AppLanguage = AppLanguage.Indonesian): List<HomeEventItem> =
        repository(backendWith(Reply.ok(EVENTS_JSON)), language).getEvents()

    @Test
    fun theMockServesSevenEventsOnePerStateInOrder() =
        runTest {
            val items = events()

            assertEquals(
                listOf(
                    "evt_coming",
                    "evt_wr_open",
                    "evt_samudra",
                    "evt_almost",
                    "evt_full",
                    "evt_soldout",
                    "evt_ended",
                ),
                items.map { it.id },
            )
            assertEquals(
                listOf("coming", "waiting", "waiting", "almost", "full", "soldOut", "ended"),
                items.map { it.status.kind() },
            )
        }

    private fun EventStatus.kind() =
        when (this) {
            is EventStatus.ComingSoon -> "coming"
            EventStatus.WaitingRoomOpen -> "waiting"
            EventStatus.OnSale -> "onSale"
            EventStatus.AlmostGone -> "almost"
            EventStatus.TemporarilyFull -> "full"
            is EventStatus.SoldOut -> "soldOut"
            is EventStatus.Ended -> "ended"
        }

    @Test
    fun anOnSaleEventMapsEveryDisplayFieldInIndonesian() =
        runTest {
            // The mock lists Samudra as waiting_room_open; the same event on sale is the plain OnSale card.
            val body = """{"items": [${EVENT_FIXTURES[2].replace("waiting_room_open", "on_sale")}]}"""
            val samudra = repository(backendWith(Reply.ok(body))).getEvents().single()

            assertEquals(
                HomeEventItem(
                    id = "evt_samudra",
                    title = "Samudra",
                    subtitle = "Tur Gelombang 2026",
                    format = EventFormat.TOUR,
                    venue = "Stadion Utama GBK",
                    city = "Jakarta",
                    dateTime = "Sab, 12 Des 2026 · 19.00",
                    timeZone = TimeZoneLabel.WIB,
                    status = EventStatus.OnSale,
                    priceFrom = "Rp 450.000",
                    posterTone = samudra.posterTone,
                ),
                samudra,
            )
            assertNotEquals(EventPosterTone.Ink, samudra.posterTone)
        }

    @Test
    fun englishUsesEnglishWeekdaysMonthsAndTwentyFourHourTime() =
        runTest {
            val samudra = events(AppLanguage.English).single { it.id == "evt_samudra" }

            assertEquals("Sat, 12 Dec 2026 · 19:00", samudra.dateTime)
        }

    @Test
    fun comingSoonCountsDownFromTheResponsesServerNowNotTheDeviceClock() =
        runTest {
            val coming = events().first()

            val status = assertIs<EventStatus.ComingSoon>(coming.status)
            // 2026-11-11T10:37:29Z to 2026-12-01T03:00:00Z
            assertEquals("19 hari 16 jam lagi", status.countdown)
            assertEquals("Sel, 1 Des · 10.00 WIB", status.opensAt)
            val english = assertIs<EventStatus.ComingSoon>(events(AppLanguage.English).first().status)
            assertEquals("19 days 16 hours left", english.countdown)
            assertEquals("Tue, 1 Dec · 10:00 WIB", english.opensAt)
        }

    @Test
    fun theServerClockStandsInWhenTheBodyHasNoServerNow() =
        runTest {
            val body = """{"items": [${EVENT_FIXTURES.first()}]}"""
            val dec1 = 1_796_094_000_000L - 90 * 60_000L // 1h30 before the 10:00 WIB opening (03:00Z)

            val items = repository(backendWith(Reply.ok(body)), now = dec1).getEvents()

            assertEquals("1 jam 30 menit lagi", assertIs<EventStatus.ComingSoon>(items.single().status).countdown)
        }

    @Test
    fun anUnsyncedClockWithoutServerNowShowsNoCountdown() =
        runTest {
            val body = """{"items": [${EVENT_FIXTURES.first()}]}"""
            // An unsynced clock counts from a small arbitrary start: reading it would give a ~20,000 day countdown.
            val unsynced =
                HomeRepositoryImpl(EventApiService(backendWith(Reply.ok(body)).api), {
                    AppLanguage.Indonesian
                }, TestServerClock(5_000L, synced = false))

            val status = assertIs<EventStatus.ComingSoon>(unsynced.getEvents().single().status)

            assertEquals("12 Des 2026", status.countdown)
            assertTrue("hari" !in status.countdown)
        }

    @Test
    fun soldOutShowsCapacityAndTheMinutesBetweenOpeningAndSelloutWhenKnown() =
        runTest {
            val soldOut = assertIs<EventStatus.SoldOut>(events().single { it.id == "evt_soldout" }.status)
            assertEquals(12_000, soldOut.capacity)
            assertEquals(0, soldOut.soldOutInMinutes, "the mock has no saleOpensAt for it")

            val withOpening =
                EVENT_FIXTURES.single { "evt_soldout" in it }
                    .replace(
                        """"soldOutAt": "2026-11-09T12:41:00+07:00"""",
                        """"saleOpensAt": "2026-11-09T12:00:00+07:00", "soldOutAt": "2026-11-09T12:41:00+07:00"""",
                    )
            val items = repository(backendWith(Reply.ok("""{"items": [$withOpening]}"""))).getEvents()
            assertEquals(41, assertIs<EventStatus.SoldOut>(items.single().status).soldOutInMinutes)
        }

    @Test
    fun endedShowsTheCloseDateInTheVenueZone() =
        runTest {
            val ended = events().single { it.id == "evt_ended" }

            assertEquals(EventStatus.Ended("30 Okt"), ended.status)
            assertEquals(EventPosterTone.Ink, ended.posterTone)
            assertEquals(EventPosterTone.Ink, events().single { it.id == "evt_soldout" }.posterTone)
        }

    @Test
    fun postersGetAStableColourFromTheEventId() =
        runTest {
            val first = events()
            val second = events()

            assertEquals(first.map { it.posterTone }, second.map { it.posterTone })
            val live = first.filter { it.status !is EventStatus.SoldOut && it.status !is EventStatus.Ended }
            assertTrue(live.all { it.posterTone != EventPosterTone.Ink })
            assertTrue(live.map { it.posterTone }.toSet().size > 1, "colours should vary between events")
        }

    @Test
    fun pricesUseDotThousandsAndAreHiddenWithoutAPrice() =
        runTest {
            val noPrice = EVENT_FIXTURES[2].replace(""""priceFrom": {"amount": 450000, "currency": "IDR"}, """, "")
            val items = repository(backendWith(Reply.ok("""{"items": [$noPrice, ${EVENT_FIXTURES[0]}]}"""))).getEvents()

            assertEquals(null, items[0].priceFrom)
            assertEquals("Rp 325.000", items[1].priceFrom)
        }

    @Test
    fun otherZonesAndFormatsAreShownInTheVenuesZone() =
        runTest {
            val wita =
                EVENT_FIXTURES[2]
                    .replace("Asia/Jakarta", "Asia/Makassar")
                    .replace(""""format": "Tour"""", """"format": "festival"""")
            val wit = EVENT_FIXTURES[3].replace("Asia/Jakarta", "Asia/Jayapura").replace("Tour", "Opera")

            val items = repository(backendWith(Reply.ok("""{"items": [$wita, $wit]}"""))).getEvents()

            assertEquals(TimeZoneLabel.WITA, items[0].timeZone)
            assertEquals("Sab, 12 Des 2026 · 20.00", items[0].dateTime)
            assertEquals(EventFormat.FESTIVAL, items[0].format)
            assertEquals(TimeZoneLabel.WIT, items[1].timeZone)
            assertEquals("Sab, 12 Des 2026 · 21.00", items[1].dateTime)
            assertEquals(EventFormat.UNKNOWN, items[1].format)
        }

    @Test
    fun anEventOfAnUnknownStateOrWithoutIdOrNameIsLeftOut() =
        runTest {
            val unknown = EVENT_FIXTURES[2].replace("waiting_room_open", "teleporting")
            val blankId = EVENT_FIXTURES[3].replace("evt_almost", "")
            val blankName = EVENT_FIXTURES[4].replace(""""name": "Kelana"""", """"name": """"")
            val body = """{"items": [$unknown, $blankId, $blankName, ${EVENT_FIXTURES[5]}]}"""

            val items = repository(backendWith(Reply.ok(body))).getEvents()

            assertEquals(listOf("evt_soldout"), items.map { it.id })
        }

    @Test
    fun aMinimalEventStillMapsAndNeverShowsInventedText() =
        runTest {
            val body = """{"items": [{"eventId": "e1", "name": "Minimal", "state": "on_sale"}]}"""

            val item = repository(backendWith(Reply.ok(body))).getEvents().single()

            assertEquals("", item.dateTime)
            assertEquals("", item.venue)
            assertEquals(null, item.priceFrom)
            assertEquals(EventFormat.UNKNOWN, item.format)
        }

    @Test
    fun anEmptyListIsAnEmptyHome() =
        runTest {
            assertEquals(emptyList(), repository(backendWith(Reply.ok("""{"items": []}"""))).getEvents())
        }

    @Test
    fun theRealHostsNotFoundBecomesAnErrorTheHomeScreenCanRetry() =
        runTest {
            val failure = assertFailsWith<DomainException> { repository(FakeBackend()).getEvents() }

            assertEquals(404, assertIs<DomainResult.Error>(failure.result).code)
        }

    @Test
    fun serverAndNetworkErrorsThrowToo() =
        runTest {
            val unavailable = backendWith(Reply.problem(503, "SERVICE_UNAVAILABLE"))
            val server = assertFailsWith<DomainException> { repository(unavailable).getEvents() }
            assertEquals(503, assertIs<DomainResult.Error>(server.result).code)

            val offline = assertFailsWith<DomainException> { repository(backendWith(Reply.offline())).getEvents() }
            assertEquals(DomainResult.NetworkError, offline.result)
        }

    @Test
    fun requestsEventsOnceWithoutQueryOrBody() =
        runTest {
            val backend = backendWith(Reply.ok(EVENTS_JSON))
            repository(backend).getEvents()

            assertEquals(listOf("GET /core/events"), backend.seen.map { it.line })
        }
}
