package com.rusuh.app.data.repository

import com.rusuh.app.data.cache.AdmissionInfo
import com.rusuh.app.data.remote.EventApiService
import com.rusuh.app.data.remote.QueueApiService
import com.rusuh.app.domain.model.DomainException
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import com.rusuh.app.domain.model.SignInRequiredException
import com.rusuh.app.feature.waitingroom.domain.model.QueuePauseReason
import com.rusuh.app.feature.waitingroom.domain.model.QueuePlace
import com.rusuh.app.feature.waitingroom.domain.model.QueueState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealQueueRepositoryTest {
    private val joinLine = "POST /queue/$EVT/join"
    private val frontierLine = "GET /queue/$EVT/frontier"
    private val meLine = "GET /queue/$EVT/me"
    private val claimLine = "POST /queue/$EVT/claim"

    private fun QueueHarness.script(
        frontiers: List<String>,
        claim: List<Reply> = listOf(Reply.ok(ADMISSION_JSON)),
        join: String = placeJson(EVT),
    ) {
        scriptBasics()
        backend.on(joinLine, Reply.ok(join))
        backend.on(frontierLine, *frontiers.map { Reply.ok(it) }.toTypedArray())
        backend.on(claimLine, *claim.toTypedArray())
    }

    private suspend fun QueueHarness.places(): List<QueuePlace> = queue.observePlace(EVT).toList()

    private suspend fun QueueHarness.failure(): Throwable {
        val outcome = runCatching { places() }
        return assertNotNull(outcome.exceptionOrNull(), "the flow should have failed")
    }

    @Test
    fun theWaitingRoomCountsDownLikeTheMockAndEndsWithTheClaimedTurn() =
        runTest {
            // The Mockoon sequence: 100..400 in steps of 30 with the operator pausing twice, then served >= seq.
            val served = listOf(100, 130, 160, 190)
            val frontiers =
                served.map { frontierJson(it) } +
                    List(2) { frontierJson(190, "paused", "operator") } +
                    listOf(220, 250, 280, 310, 340, 370, 400).map { frontierJson(it) }
            val h = QueueHarness()
            h.script(frontiers, claim = listOf(Reply.problem(409, "NOT_YET"), Reply.ok(ADMISSION_JSON)))

            val places = h.places()

            val waiting = places.dropLast(1)
            assertEquals(
                listOf(100, 130, 160, 190, 190, 190, 220, 250, 280, 310, 340, 370),
                waiting.map { it.served },
            )
            assertEquals(
                listOf(300, 270, 240, 210, 210, 210, 180, 150, 120, 90, 60, 30),
                waiting.map { it.seq - it.served },
                "position = seq - served",
            )
            assertEquals(
                List(4) { QueueState.Open } + List(2) { QueueState.Paused } + List(6) { QueueState.Open },
                waiting.map { it.state },
            )
            assertTrue(waiting.all { it.seq == 400 && it.ratePerMinute == 300 })
            assertEquals("Samudra", waiting.first().event.title)
            assertEquals("Stadion Utama GBK, Jakarta", waiting.first().event.venueLine)

            val turn = places.last()
            assertEquals(QueueState.Turn, turn.state)
            assertEquals(
                turn.serverNowEpochMillis + 60_000L,
                turn.turnExpiresAtEpochMillis,
                "60 s window on server time",
            )
            assertEquals(1, places.count { it.state == QueueState.Turn }, "the 60 s countdown is not re-emitted")
            assertEquals("mock-admission-token", h.tokens.getAdmissionToken())
            assertEquals(
                AdmissionInfo(EVT, "s_mock_01", 3, FIXTURE_NOW_MILLIS + 300_000L),
                h.queueCache.getAdmission(),
                "details for Detail are kept without the secret",
            )
            assertEquals(2, h.backend.callsTo(claimLine))
        }

    @Test
    fun joiningStoresTheQueueTokenAndRemembersTheEventForMyQueues() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(100)))

            h.queue.observePlace(EVT).first()

            assertEquals("mock-queue-token-$EVT", h.tokens.getQueueToken(EVT))
            assertEquals(listOf(EVT), h.queueCache.getJoinedEventIds())
            assertEquals(
                listOf("POST /queue/devices", joinLine, "GET /core/events", frontierLine),
                h.backend.seen.map { it.line },
            )
            assertNull(h.backend.seen.single { it.line == joinLine }.headers["X-Queue-Token"])
        }

    @Test
    fun theFirstCallRegistersTheDeviceBeforeJoining() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(100)))

            h.queue.observePlace(EVT).first()

            assertEquals("POST /queue/devices", h.backend.seen.first().line)
            assertEquals("mock-device-token", h.tokens.getDeviceToken())
        }

    @Test
    fun pollingFollowsTheDistanceBandsWithPlusMinusTwentyPercentJitter() =
        runTest {
            // seq 50,000: 49,900 places back (10 s), 5,000 back (5 s), 900 back (2 s), then the queue closes.
            val h = QueueHarness()
            val frontiers =
                listOf(0, 0, 20_000, 45_000, 49_100, 49_100, 49_100).map { frontierJson(it) } +
                    frontierJson(49_100, "closed", "sold_out")
            h.script(frontiers, join = placeJson(EVT, seq = 50_000, served = 0))

            val places = h.places()

            assertEquals(QueueState.Closed, places.last().state)
            val bands = listOf(50_000, 50_000, 30_000, 5_000, 900, 900, 900)
            assertEquals(bands.size, h.delays.size, "one wait between every two polls")
            bands.zip(h.delays).forEach { (position, waited) ->
                val base =
                    when {
                        position > 10_000 -> 10_000.0
                        position > 1_000 -> 5_000.0
                        else -> 2_000.0
                    }
                assertTrue(waited >= base * 0.8 - 1 && waited <= base * 1.2 + 1, "$position back waited $waited ms")
            }
            assertTrue(h.delays.toSet().size > 1, "jitter must vary the waits: ${h.delays}")
        }

    @Test
    fun pausedReasonsMapToTheDomainReasons() =
        runTest {
            val h = QueueHarness()
            h.script(
                listOf(
                    frontierJson(100, "paused", "stock"),
                    frontierJson(100, "paused", "operator"),
                    frontierJson(100, "closed", "sold_out"),
                ),
            )

            val places = h.places()

            assertEquals(QueuePauseReason.StockFull, places[0].pauseReason)
            assertEquals(QueuePauseReason.OperatorCheck, places[1].pauseReason)
            assertTrue(places.take(2).all { it.state == QueueState.Paused })
        }

    @Test
    fun preOpenCountsToTheOpeningThenFetchesTheNumberOnceWithRandomDelay() =
        runTest {
            val opensAtSeconds = FIXTURE_NOW_MILLIS / 1_000L + 90 // 17:38:59 WIB
            val h = QueueHarness()
            h.script(
                listOf(
                    frontierJson(0, "preopen", rate = 0, opensAt = opensAtSeconds),
                    frontierJson(0, "open"),
                    frontierJson(10, "open"),
                    frontierJson(10, "closed", "sold_out"),
                ),
                join = placeJson(EVT, "preopen", seq = null),
            )
            h.backend.on(meLine, Reply.ok(placeJson(EVT, seq = 400, served = 0)))

            val places = h.places()

            val preOpen = places.first()
            assertEquals(QueueState.PreOpen, preOpen.state)
            assertEquals(opensAtSeconds * 1_000L, preOpen.opensAtEpochMillis)
            assertEquals("17.38 WIB", preOpen.opensAtLabel)
            assertNull(preOpen.ratePerMinute)
            assertEquals(
                listOf(QueueState.PreOpen, QueueState.Open, QueueState.Open, QueueState.Closed),
                places.map { it.state },
            )
            assertEquals(400, places[1].seq)
            assertEquals(1, h.backend.callsTo(meLine), "the number is fetched once")
            assertEquals("mock-queue-token-$EVT", h.backend.seen.single { it.line == meLine }.headers["X-Queue-Token"])
            assertTrue(h.delays.any { it in 0..30_000L })
        }

    @Test
    fun theTurnDeadlineComesFromMeWhenTheServerGivesIt() =
        runTest {
            val deadline = "2026-11-11T17:38:10+07:00" // FIXTURE_NOW + 41 s
            val h = QueueHarness()
            h.script(listOf(frontierJson(400)), claim = listOf(Reply.ok(ADMISSION_JSON)))
            h.backend.on(meLine, Reply.ok(placeJson(EVT, served = 400, extra = ""","turnExpiresAt":"$deadline"""")))

            val places = h.places()

            assertEquals(FIXTURE_NOW_MILLIS + 41_000L, places.first().turnExpiresAtEpochMillis)
        }

    @Test
    fun claimingSendsTheQueueTokenAndRetriesNotYetThenFullWithinTheWindow() =
        runTest {
            val h = QueueHarness()
            h.script(
                listOf(frontierJson(400)),
                claim = listOf(Reply.problem(409, "NOT_YET"), Reply.problem(409, "FULL"), Reply.ok(ADMISSION_JSON)),
            )

            h.places()

            assertEquals(3, h.backend.callsTo(claimLine))
            h.backend.seen.filter { it.line == claimLine }.forEach {
                assertEquals("mock-queue-token-$EVT", it.headers["X-Queue-Token"])
            }
            val (notYet, full) = h.delays
            assertTrue(notYet in 800L..1_200L, "NOT_YET waits about 1 s: $notYet")
            assertTrue(full in 1_000L..2_400L, "FULL waits 1-2 s: $full")
        }

    @Test
    fun anExhaustedTurnWindowIsMissed() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(400)), claim = listOf(Reply.problem(409, "NOT_YET")))

            val places = h.places()

            assertEquals(listOf(QueueState.Turn, QueueState.Missed), places.map { it.state })
            assertEquals(60_000L, h.delays.sum(), "waits are cut to exactly the 60 s window")
            assertTrue(h.backend.callsTo(claimLine) > 40, "retried about every second")
            assertNull(h.tokens.getQueueToken(EVT), "a missed place drops its token")
            assertNull(h.tokens.getAdmissionToken())
            assertEquals(emptyList(), h.queueCache.getJoinedEventIds())
        }

    @Test
    fun aMissedClaimEmitsMissed() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(400)), claim = listOf(Reply.problem(410, "MISSED")))

            val places = h.places()

            assertEquals(listOf(QueueState.Turn, QueueState.Missed), places.map { it.state })
            assertEquals(1, h.backend.callsTo(claimLine))
            assertNull(h.tokens.getAdmissionToken())
        }

    @Test
    fun aMissedPlaceReportedByMeSkipsTheClaim() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(400)))
            h.backend.on(meLine, Reply.problem(410, "MISSED"))

            val places = h.places()

            assertEquals(listOf(QueueState.Missed), places.map { it.state })
            assertEquals(0, h.backend.callsTo(claimLine))
        }

    @Test
    fun noStockOnClaimGoesBackToTheWaitingRoom() =
        runTest {
            val h = QueueHarness()
            h.script(
                listOf(
                    frontierJson(400),
                    frontierJson(400, "paused", "stock"),
                    frontierJson(400, "closed", "sold_out"),
                ),
                claim = listOf(Reply.problem(409, "NO_STOCK")),
            )

            val places = h.places()

            assertEquals(listOf(QueueState.Turn, QueueState.Paused, QueueState.Closed), places.map { it.state })
            assertEquals(QueuePauseReason.StockFull, places[1].pauseReason)
        }

    @Test
    fun aGuestWhoIsAskedToSignInFailsTheFlowAfterShowingTheTurn() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(400)), claim = listOf(Reply.problem(401, "UNAUTHORIZED")))
            val seen = mutableListOf<QueuePlace>()

            val failure =
                assertFailsWith<SignInRequiredException> {
                    h.queue.observePlace(EVT).collect { seen += it }
                }

            assertNotNull(failure)
            assertEquals(listOf(QueueState.Turn), seen.map { it.state })
            assertNull(h.tokens.getAdmissionToken())
        }

    @Test
    fun aClosedQueueShowsSoldOutAndForgetsTheTokenAndTheEvent() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(100), frontierJson(100, "closed", "sold_out")))

            val places = h.places()

            assertEquals(listOf(QueueState.Open, QueueState.Closed), places.map { it.state })
            assertNull(h.tokens.getQueueToken(EVT))
            assertEquals(emptyList(), h.queueCache.getJoinedEventIds())
        }

    @Test
    fun aQueueThatIsClosedAtJoinNeverStoresATokenOrAnEvent() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(41_250, "closed", "ended")), join = placeJson(EVT, "closed", seq = null))

            val places = h.places()

            assertEquals(listOf(QueueState.Closed), places.map { it.state })
            assertNull(h.tokens.getQueueToken(EVT))
            assertEquals(emptyList(), h.queueCache.getJoinedEventIds())
        }

    @Test
    fun theRealHostAnsweringNotFoundToEverythingFailsTheFlowInsteadOfCrashing() =
        runTest {
            val h = QueueHarness() // nothing scripted: every route is a 404 like the real host today

            val failure = h.failure()

            val error = assertIs<DomainResult.Error>(assertIs<DomainException>(failure).result)
            assertEquals(404, error.code)
            assertEquals(listOf("POST /queue/devices"), h.backend.seen.map { it.line })
        }

    @Test
    fun aNotFoundOnJoinFrontierAndEventsStillFailsCleanly() =
        runTest {
            val h = QueueHarness()
            h.scriptBasics()
            h.backend.on("GET /core/events", Reply.notFound())

            val joinFailure = assertIs<DomainException>(h.failure())
            assertEquals(404, assertIs<DomainResult.Error>(joinFailure.result).code)

            h.backend.on(joinLine, Reply.ok(placeJson(EVT)))
            val frontierFailure = assertIs<DomainException>(h.failure())
            assertEquals(404, assertIs<DomainResult.Error>(frontierFailure.result).code)
        }

    @Test
    fun aNetworkFailureAndAServerErrorFailTheFlow() =
        runTest {
            val offline = QueueHarness()
            offline.script(listOf(frontierJson(100)))
            offline.backend.on(frontierLine, Reply.offline())
            assertEquals(DomainResult.NetworkError, assertIs<DomainException>(offline.failure()).result)

            val broken = QueueHarness()
            broken.script(listOf(frontierJson(100)))
            broken.backend.on(frontierLine, Reply.problem(503, "SERVICE_UNAVAILABLE", retryAfter = 7))
            val error = assertIs<DomainResult.Error>(assertIs<DomainException>(broken.failure()).result)
            assertEquals(503, error.code)
            assertEquals(ErrorCode.SERVICE_UNAVAILABLE, error.errorCode)
            assertEquals(7, error.retryAfterSeconds)
        }

    @Test
    fun aRejectedDeviceTokenOnJoinRegistersAgainOnceAndRetries() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(100)))
            h.backend.on(joinLine, Reply.problem(401, "UNAUTHORIZED"), Reply.ok(placeJson(EVT)))

            h.queue.observePlace(EVT).first()

            assertEquals(2, h.backend.callsTo("POST /queue/devices"))
            assertEquals(2, h.backend.callsTo(joinLine))
        }

    @Test
    fun aSecondRejectionAfterReRegisteringFailsInsteadOfLooping() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(100)))
            h.backend.on(joinLine, Reply.problem(401, "UNAUTHORIZED"))

            val error = assertIs<DomainResult.Error>(assertIs<DomainException>(h.failure()).result)

            assertEquals(401, error.code)
            assertEquals(2, h.backend.callsTo("POST /queue/devices"))
            assertEquals(2, h.backend.callsTo(joinLine))
        }

    @Test
    fun aDeviceRegistrationFailureFailsTheFlowBeforeJoining() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(100)))
            h.backend.on("POST /queue/devices", Reply.problem(429, "RATE_LIMITED", retryAfter = 30))

            val error = assertIs<DomainResult.Error>(assertIs<DomainException>(h.failure()).result)

            assertEquals(429, error.code)
            assertEquals(30, error.retryAfterSeconds)
            assertEquals(0, h.backend.callsTo(joinLine))
        }

    @Test
    fun aBlankEventHasNoPlace() =
        runTest {
            val h = QueueHarness()

            assertEquals(emptyList(), h.queue.observePlace(" ").toList())
            assertEquals(emptyList(), h.backend.seen)
        }

    @Test
    fun theTitleFallsBackToTheEventIdWhenTheEventListIsUnavailable() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(100)))
            h.backend.on("GET /core/events", Reply.notFound())

            val place = h.queue.observePlace(EVT).first()

            assertEquals(EVT, place.event.title)
        }

    @Test
    fun cancellingTheCollectorStopsPollingAndCancelsTheWait() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(100)))
            val waiting = CompletableDeferred<Unit>()
            var waitWasCancelled = false
            val queue =
                RealQueueRepository(
                    queueApi = QueueApiService(h.backend.api),
                    eventApi = EventApiService(h.backend.api),
                    devices = h.devices,
                    tokens = h.tokens,
                    cache = h.queueCache,
                    serverClock = h.clock,
                    localeProvider = h.locale,
                    pacing =
                        QueuePacing(
                            delay = {
                                waiting.complete(Unit)
                                try {
                                    awaitCancellation()
                                } catch (cancelled: CancellationException) {
                                    waitWasCancelled = true
                                    throw cancelled
                                }
                            },
                        ),
                )
            val places = mutableListOf<QueuePlace>()
            val collector = launch(Dispatchers.Default) { queue.observePlace(EVT).collect { places += it } }

            waiting.await() // the first place is out and the collector sits in the wait before the next poll
            collector.cancelAndJoin()

            assertTrue(waitWasCancelled)
            assertEquals(1, places.size)
            assertEquals(1, h.backend.callsTo(frontierLine), "no poll after the cancellation")
        }

    @Test
    fun leavingDeletesThePlaceWithItsQueueTokenAndForgetsIt() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(100)))
            h.backend.on("DELETE /queue/$EVT/me", Reply.noContent())
            h.queue.observePlace(EVT).first()

            val result = h.queue.leave(EVT)

            assertEquals(DomainResult.Success(Unit), result)
            val delete = h.backend.seen.single { it.line == "DELETE /queue/$EVT/me" }
            assertEquals("mock-queue-token-$EVT", delete.headers["X-Queue-Token"])
            assertNull(h.tokens.getQueueToken(EVT))
            assertEquals(emptyList(), h.queueCache.getJoinedEventIds())
        }

    @Test
    fun aFailedLeaveKeepsThePlace() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(100)))
            h.queue.observePlace(EVT).first()
            h.backend.on("DELETE /queue/$EVT/me", Reply.problem(503, "SERVICE_UNAVAILABLE"))

            val result = h.queue.leave(EVT)

            assertEquals(503, assertIs<DomainResult.Error>(result).code)
            assertEquals("mock-queue-token-$EVT", h.tokens.getQueueToken(EVT))
            assertEquals(listOf(EVT), h.queueCache.getJoinedEventIds())
        }

    @Test
    fun leavingWithoutAPlaceSucceedsWithoutACall() =
        runTest {
            val h = QueueHarness()

            assertEquals(DomainResult.Success(Unit), h.queue.leave(EVT))
            assertEquals(emptyList(), h.backend.seen)
        }

    @Test
    fun theDelaysOfTheCountdownStayWithinTheClosestBand() =
        runTest {
            val h = QueueHarness()
            h.script(listOf(frontierJson(100), frontierJson(130), frontierJson(160, "closed", "sold_out")))

            h.places()

            assertContentEquals(listOf(true, true), h.delays.map { it in 1_600L..2_400L })
        }

    private companion object {
        const val EVT = "evt_samudra"
    }
}
