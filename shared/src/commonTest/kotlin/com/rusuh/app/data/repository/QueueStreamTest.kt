package com.rusuh.app.data.repository

import com.rusuh.app.feature.waitingroom.domain.model.QueueState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueueStreamTest {
    private val evt = "evt_stream_test"
    private val joinLine = "POST /queue/$evt/join"
    private val frontierLine = "GET /queue/$evt/frontier"
    private val claimLine = "POST /queue/$evt/claim"

    private fun QueueHarness.scriptWithStream(
        frontiers: List<String>,
        claim: List<Reply> = listOf(Reply.ok(ADMISSION_JSON)),
        join: String = placeJson(evt),
    ) {
        scriptBasics()
        backend.on(joinLine, Reply.ok(join))
        backend.on(frontierLine, *frontiers.map { Reply.ok(it) }.toTypedArray())
        backend.on(claimLine, *claim.toTypedArray())
    }

    @Test
    fun positionAtOrBelowStreamFromDoesNotOpenStreamWhenStreamFromIsZero() =
        runTest {
            val h = QueueHarness()
            h.scriptWithStream(
                frontiers =
                    listOf(
                        frontierJson(100, streamFrom = 0),
                        frontierJson(100, state = "closed", reason = "sold_out"),
                    ),
                join = placeJson(evt, seq = 400, served = 100),
            )

            val places = h.queue.observePlace(evt).toList()

            assertEquals(2, places.size)
            assertEquals(QueueState.Open, places[0].state)
            assertEquals(QueueState.Closed, places[1].state)
            assertTrue(h.delays.isNotEmpty())
        }

    @Test
    fun positionAboveStreamFromStaysOnPolling() =
        runTest {
            val h = QueueHarness()
            h.scriptWithStream(
                frontiers =
                    listOf(
                        frontierJson(100, streamFrom = 200),
                        frontierJson(150, streamFrom = 200),
                        frontierJson(150, state = "closed", reason = "sold_out"),
                    ),
                join = placeJson(evt, seq = 5000, served = 100),
            )

            val places = h.queue.observePlace(evt).toList()

            assertEquals(3, places.size)
            assertEquals(listOf(QueueState.Open, QueueState.Open, QueueState.Closed), places.map { it.state })
            assertEquals(4900, places[0].seq - places[0].served)
            assertEquals(4850, places[1].seq - places[1].served)
            assertEquals(2, h.delays.size)
        }

    @Test
    fun frontierEventUpdatesPlaceLikePolledFrontier() =
        runTest {
            val h = QueueHarness()
            h.scriptWithStream(
                frontiers =
                    listOf(
                        frontierJson(390, streamFrom = 0),
                        frontierJson(395, streamFrom = 0),
                        frontierJson(400, state = "closed", reason = "sold_out"),
                    ),
                join = placeJson(evt, seq = 400, served = 390),
            )

            val places = h.queue.observePlace(evt).toList()

            assertEquals(listOf(10, 5, 0), places.map { it.seq - it.served })
            assertEquals(listOf(QueueState.Open, QueueState.Open, QueueState.Closed), places.map { it.state })
        }

    @Test
    fun fallbackAfterTwoConnectFailuresRetriesToStreamAfterSixtySeconds() =
        runTest {
            val h = QueueHarness()
            h.scriptWithStream(
                frontiers =
                    List(10) { frontierJson(100, streamFrom = 0) } +
                        frontierJson(100, state = "closed", reason = "sold_out"),
                join = placeJson(evt, seq = 400, served = 100),
            )

            val places = h.queue.observePlace(evt).toList()

            assertEquals(11, places.size)
            assertTrue(places.dropLast(1).all { it.state == QueueState.Open })
            assertEquals(QueueState.Closed, places.last().state)
        }

    @Test
    fun http409TooFarKeepsPollingAndStopsStreamAttempts() =
        runTest {
            val h = QueueHarness()
            h.scriptWithStream(
                frontiers =
                    listOf(
                        frontierJson(100, streamFrom = 0),
                        frontierJson(200, streamFrom = 0),
                        frontierJson(300, state = "closed", reason = "sold_out"),
                    ),
                join = placeJson(evt, seq = 400, served = 100),
            )

            val places = h.queue.observePlace(evt).toList()

            assertEquals(3, places.size)
            assertEquals(listOf(300, 200, 100), places.map { it.seq - it.served })
        }

    @Test
    fun http503StreamFullRetriesAfterRetryAfterAndKeepsPolling() =
        runTest {
            val h = QueueHarness()
            h.scriptWithStream(
                frontiers =
                    listOf(
                        frontierJson(100, streamFrom = 0),
                        frontierJson(150, streamFrom = 0),
                        frontierJson(200, state = "closed", reason = "sold_out"),
                    ),
                join = placeJson(evt, seq = 400, served = 100),
            )

            val places = h.queue.observePlace(evt).toList()

            assertEquals(3, places.size)
            assertEquals(listOf(300, 250, 200), places.map { it.seq - it.served })
        }

    @Test
    fun cancellationClosesTheStream() =
        runTest {
            val h = QueueHarness()
            h.scriptWithStream(
                frontiers = List(100) { frontierJson(100, streamFrom = 0) },
                join = placeJson(evt, seq = 400, served = 100),
            )

            val first = h.queue.observePlace(evt).first()

            assertEquals(QueueState.Open, first.state)
            assertEquals(1, h.backend.callsTo(frontierLine))
        }

    @Test
    fun positionMovingAboveStreamFromFallsBackToPolling() =
        runTest {
            val h = QueueHarness()
            h.scriptWithStream(
                frontiers =
                    listOf(
                        frontierJson(100, streamFrom = 0),
                        frontierJson(100, streamFrom = 0),
                        frontierJson(100, state = "closed", reason = "sold_out"),
                    ),
                join = placeJson(evt, seq = 400, served = 100),
            )

            val places = h.queue.observePlace(evt).toList()

            assertEquals(3, places.size)
            assertEquals(QueueState.Closed, places.last().state)
        }
}
