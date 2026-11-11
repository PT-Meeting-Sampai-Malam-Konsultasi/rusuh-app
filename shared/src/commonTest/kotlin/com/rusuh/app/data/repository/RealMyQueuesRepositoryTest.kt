package com.rusuh.app.data.repository

import com.rusuh.app.domain.model.DomainException
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.MyQueue
import com.rusuh.app.domain.model.MyQueuePhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealMyQueuesRepositoryTest {
    private suspend fun QueueHarness.joined(vararg ids: String) {
        ids.forEach { id ->
            tokens.setQueueToken(id, "token-$id")
            queueCache.addJoinedEvent(id)
        }
    }

    private fun QueueHarness.place(
        id: String,
        me: Reply,
        frontier: Reply,
    ) {
        backend.on("GET /queue/$id/me", me)
        backend.on("GET /queue/$id/frontier", frontier)
    }

    private fun QueueHarness.at(
        id: String,
        served: Int,
        state: String = "open",
        reason: String? = null,
        seq: Int? = 400,
    ) = place(
        id,
        me = Reply.ok(placeJson(id, state, seq, served)),
        frontier = Reply.ok(frontierJson(served, state, reason)),
    )

    @Test
    fun noJoinedQueuesIsAnEmptyListWithoutAnyCall() =
        runTest {
            val h = QueueHarness()

            assertEquals(emptyList(), h.myQueues.getMyQueues())
            assertEquals(emptyList(), h.backend.seen)
        }

    @Test
    fun everyPhaseMapsFromTheStateAndTheNumbers() =
        runTest {
            val h = QueueHarness()
            h.backend.on("GET /events", Reply.ok(EVENTS_JSON))
            h.joined("evt_samudra", "evt_full", "evt_almost", "evt_wr_open")
            h.at("evt_samudra", served = 100) // 300 back, rate 300/min: 1 minute
            h.at("evt_full", served = 190, state = "paused", reason = "stock")
            h.at("evt_almost", served = 400) // served >= seq
            h.place(
                "evt_wr_open",
                me = Reply.ok(placeJson("evt_wr_open", "preopen", seq = null)),
                frontier = Reply.ok(frontierJson(0, "preopen", rate = 0, opensAt = 1_794_393_539)),
            )

            val queues = h.myQueues.getMyQueues()

            assertEquals(
                listOf(
                    MyQueue(
                        "evt_samudra",
                        "Samudra",
                        "12 Des 2026 · Stadion Utama GBK, Jakarta",
                        300,
                        100,
                        MyQueuePhase.Live,
                        0,
                        etaMinutes = 1,
                    ),
                    MyQueue(
                        "evt_full",
                        "Kelana",
                        "12 Des 2026 · Stadion Utama GBK, Jakarta",
                        210,
                        190,
                        MyQueuePhase.Paused,
                        0,
                    ),
                    MyQueue(
                        "evt_almost",
                        "Pelita",
                        "12 Des 2026 · Stadion Utama GBK, Jakarta",
                        0,
                        400,
                        MyQueuePhase.YourTurn,
                        0,
                    ),
                    MyQueue(
                        "evt_wr_open",
                        "Nada Pantai",
                        "12 Des 2026 · Stadion Utama GBK, Jakarta",
                        1,
                        null,
                        MyQueuePhase.QueueOpen,
                        0,
                    ),
                ),
                queues,
            )
        }

    @Test
    fun theEtaRoundsUpAndIsHiddenWithoutARate() =
        runTest {
            val h = QueueHarness()
            h.joined("a", "b")
            h.place("a", Reply.ok(placeJson("a", seq = 1_000)), Reply.ok(frontierJson(0, rate = 300)))
            h.place("b", Reply.ok(placeJson("b", seq = 1_000)), Reply.ok(frontierJson(0, rate = 0)))

            val (a, b) = h.myQueues.getMyQueues()

            assertEquals(4, a.etaMinutes)
            assertNull(b.etaMinutes)
        }

    @Test
    fun theEventIdIsTheTitleWhenTheEventListCannotBeLoaded() =
        runTest {
            val h = QueueHarness() // GET /events is a 404
            h.joined("evt_samudra")
            h.at("evt_samudra", served = 100)

            val queue = h.myQueues.getMyQueues().single()

            assertEquals("evt_samudra", queue.title)
            assertEquals("", queue.dateVenue)
        }

    @Test
    fun aClosedQueueIsDroppedAndForgotten() =
        runTest {
            val h = QueueHarness()
            h.joined("evt_samudra", "evt_soldout")
            h.at("evt_samudra", served = 100)
            h.at("evt_soldout", served = 100, state = "closed", reason = "sold_out", seq = null)

            val queues = h.myQueues.getMyQueues()

            assertEquals(listOf("evt_samudra"), queues.map { it.eventId })
            assertEquals(listOf("evt_samudra"), h.queueCache.getJoinedEventIds())
            assertNull(h.tokens.getQueueToken("evt_soldout"))
        }

    @Test
    fun aClosedFrontierDropsAnOtherwiseOpenPlace() =
        runTest {
            val h = QueueHarness()
            h.joined("evt_samudra")
            h.place("evt_samudra", Reply.ok(placeJson("evt_samudra")), Reply.ok(frontierJson(100, "closed", "ended")))

            assertEquals(emptyList(), h.myQueues.getMyQueues())
            assertEquals(emptyList(), h.queueCache.getJoinedEventIds())
        }

    @Test
    fun aPlaceThatEndedIsDroppedAndForgotten() =
        runTest {
            val h = QueueHarness()
            h.joined("evt_samudra")
            h.place("evt_samudra", Reply.problem(410, "MISSED"), Reply.ok(frontierJson(100)))

            assertEquals(emptyList(), h.myQueues.getMyQueues())
            assertEquals(emptyList(), h.queueCache.getJoinedEventIds())
        }

    @Test
    fun anEntryThatAnswersNotFoundIsDroppedButKeptStored() =
        runTest {
            val h = QueueHarness() // the real host today: everything is a 404
            h.joined("evt_samudra")

            assertEquals(emptyList(), h.myQueues.getMyQueues())
            assertEquals(listOf("evt_samudra"), h.queueCache.getJoinedEventIds())
            assertEquals("token-evt_samudra", h.tokens.getQueueToken("evt_samudra"))
        }

    @Test
    fun aMissingTokenDropsTheEntry() =
        runTest {
            val h = QueueHarness()
            h.queueCache.addJoinedEvent("evt_samudra")

            assertEquals(emptyList(), h.myQueues.getMyQueues())
            assertEquals(emptyList(), h.queueCache.getJoinedEventIds())
            assertEquals(0, h.backend.callsTo("GET /queue/evt_samudra/me"))
        }

    @Test
    fun aRejectedTokenDropsAndForgetsTheEntryWhileTheOthersStay() =
        runTest {
            val h = QueueHarness()
            h.joined("good", "stale")
            h.at("good", served = 100)
            h.place("stale", Reply.problem(401, "UNAUTHORIZED"), Reply.ok(frontierJson(100)))

            val queues = h.myQueues.getMyQueues()

            assertEquals(listOf("good"), queues.map { it.eventId })
            assertEquals(listOf("good"), h.queueCache.getJoinedEventIds())
            assertNull(h.tokens.getQueueToken("stale"))
        }

    @Test
    fun oneEntryWithATransientFailureDoesNotBreakTheListAndStaysStored() =
        runTest {
            val h = QueueHarness()
            h.joined("good", "flaky")
            h.at("good", served = 100)
            h.place("flaky", Reply.problem(503, "SERVICE_UNAVAILABLE"), Reply.ok(frontierJson(100)))

            val queues = h.myQueues.getMyQueues()

            assertEquals(listOf("good"), queues.map { it.eventId })
            assertEquals(listOf("good", "flaky"), h.queueCache.getJoinedEventIds())
            assertEquals("token-flaky", h.tokens.getQueueToken("flaky"))
        }

    @Test
    fun anOfflineEntryAndANotFoundEntryDoNotBreakTheListEither() =
        runTest {
            val h = QueueHarness()
            h.joined("good", "offline", "gone")
            h.at("good", served = 100)
            h.place("offline", Reply.offline(), Reply.ok(frontierJson(100)))
            // "gone" is unscripted: 404 everywhere

            assertEquals(listOf("good"), h.myQueues.getMyQueues().map { it.eventId })
        }

    @Test
    fun theLoadThrowsOnlyWhenEveryEntryFailed() =
        runTest {
            val h = QueueHarness()
            h.joined("a", "b", "c")
            listOf(
                "a",
                "b",
                "c",
            ).forEach { h.place(it, Reply.problem(503, "SERVICE_UNAVAILABLE"), Reply.ok(frontierJson(1))) }

            val failure = assertFailsWith<DomainException> { h.myQueues.getMyQueues() }

            assertEquals(503, assertIs<DomainResult.Error>(failure.result).code)
        }

    @Test
    fun entriesNotFoundEverywhereAreAnEmptyListNotAnError() =
        runTest {
            val h = QueueHarness()
            h.joined("a", "b")

            assertEquals(emptyList(), h.myQueues.getMyQueues())
        }

    @Test
    fun entriesAreReadInParallelUpToTheCapAndKeepTheirOrder() =
        runTest {
            val ids = (1..10).map { "evt$it" }
            val h = QueueHarness()
            h.joined(*ids.toTypedArray())
            ids.forEach { h.at(it, served = 100) }
            val gate = Mutex()
            var running = 0
            var peak = 0
            h.backend.onRequest = { line ->
                if (line.endsWith("/me")) {
                    gate.withLock {
                        running++
                        peak = maxOf(peak, running)
                    }
                    delay(40)
                    gate.withLock { running-- }
                }
            }

            val queues = h.myQueues.getMyQueues()

            assertEquals(ids, queues.map { it.eventId })
            assertTrue(peak in 2..RealMyQueuesRepository.DEFAULT_PARALLEL, "peak parallel reads: $peak")
        }

    @Test
    fun serverAndNetworkFailuresFailTheLoad() =
        runTest {
            val server = QueueHarness()
            server.joined("evt_samudra")
            server.place("evt_samudra", Reply.problem(503, "SERVICE_UNAVAILABLE"), Reply.ok(frontierJson(100)))
            val error = assertFailsWith<DomainException> { server.myQueues.getMyQueues() }
            assertEquals(503, assertIs<DomainResult.Error>(error.result).code)

            val offline = QueueHarness()
            offline.joined("evt_samudra")
            offline.place("evt_samudra", Reply.ok(placeJson("evt_samudra")), Reply.offline())
            val failure = assertFailsWith<DomainException> { offline.myQueues.getMyQueues() }
            assertEquals(DomainResult.NetworkError, failure.result)
        }
}
