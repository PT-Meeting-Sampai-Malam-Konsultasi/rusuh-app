package com.rusuh.app.data.repository

import com.rusuh.app.domain.model.DomainException
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.feature.waitingroom.domain.model.QueuePlace
import com.rusuh.app.feature.waitingroom.domain.model.QueueState
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Draw outcomes, transient failures, claim edge cases and the unsynced clock of [RealQueueRepository]. */
class QueueResilienceTest {
    private val joinLine = "POST /queue/$EVT/join"
    private val frontierLine = "GET /queue/$EVT/frontier"
    private val meLine = "GET /queue/$EVT/me"
    private val claimLine = "POST /queue/$EVT/claim"

    private fun ok(body: String) = Reply.ok(body)

    private fun unavailable(retryAfter: Int? = null) = Reply.problem(503, "SERVICE_UNAVAILABLE", retryAfter)

    private fun QueueHarness.script(
        frontiers: List<Reply>,
        join: String = placeJson(EVT),
        claim: List<Reply> = listOf(ok(ADMISSION_JSON)),
    ) {
        scriptBasics()
        backend.on(joinLine, ok(join))
        backend.on(frontierLine, *frontiers.toTypedArray())
        backend.on(claimLine, *claim.toTypedArray())
    }

    private suspend fun QueueHarness.places(): List<QueuePlace> = queue.observePlace(EVT).toList()

    private fun open(served: Int) = ok(frontierJson(served))

    private val closed = ok(frontierJson(0, "closed", "sold_out"))

    // region: the pre-sale draw

    private fun QueueHarness.scriptDraw(vararg me: Reply) {
        script(
            frontiers = listOf(open(0), open(0), open(0), open(0), closed),
            join = placeJson(EVT, "preopen", seq = null),
        )
        backend.on(meLine, *me)
    }

    @Test
    fun aMissedPlaceAfterTheDrawEndsTheRunInsteadOfLoopingForever() =
        runTest {
            val h = QueueHarness()
            h.script(frontiers = listOf(open(0)), join = placeJson(EVT, "preopen", seq = null)) // open forever
            h.backend.on(meLine, Reply.problem(410, "MISSED"))

            val places = h.places()

            assertEquals(listOf(QueueState.Missed), places.map { it.state })
            assertEquals(1, h.backend.callsTo(meLine))
            assertNull(h.tokens.getQueueToken(EVT))
        }

    @Test
    fun aClosedPlaceAfterTheDrawEndsAsSoldOut() =
        runTest {
            val h = QueueHarness()
            h.script(frontiers = listOf(open(0)), join = placeJson(EVT, "preopen", seq = null))
            h.backend.on(meLine, Reply.problem(410, "QUEUE_CLOSED"))

            assertEquals(listOf(QueueState.Closed), h.places().map { it.state })
        }

    @Test
    fun notDrawnYetRetriesOnTheNextPollAndJittersOnlyOncePerDraw() =
        runTest {
            val h = QueueHarness()
            // 404 = no place yet; a place without a number = not drawn yet; then the number arrives.
            h.scriptDraw(
                Reply.notFound(),
                ok(placeJson(EVT, seq = null)),
                ok(placeJson(EVT, seq = 400, served = 0)),
            )

            val places = h.places()

            assertEquals(listOf(QueueState.Open, QueueState.Open, QueueState.Closed), places.map { it.state })
            assertEquals(3, h.backend.callsTo(meLine), "asked again at each poll until the number arrived")
            // one random 0-30 s wait for the whole draw, plus one poll wait after each of the first four frontiers
            assertEquals(1 + 4, h.delays.size, "${h.delays}")
            assertTrue(h.delays.first() in 0..30_000L)
        }

    @Test
    fun aServerErrorOnMeDuringTheDrawIsRetriedWithBackoff() =
        runTest {
            val h = QueueHarness()
            h.scriptDraw(unavailable(), ok(placeJson(EVT, seq = 400, served = 0)))

            val places = h.places()

            assertEquals(QueueState.Closed, places.last().state)
            assertTrue(2_000L in h.delays, "backed off 2 s once: ${h.delays}")
            assertEquals(400, places.first().seq)
        }

    // endregion

    // region: transient failures

    @Test
    fun aBlipAtPosition800RecoversAndKeepsWaiting() =
        runTest {
            val h = QueueHarness()
            h.script(
                frontiers = listOf(open(200), unavailable(), open(300), closed),
                join = placeJson(EVT, seq = 1_000, served = 200),
            )

            val places = h.places()

            assertEquals(listOf(QueueState.Open, QueueState.Open, QueueState.Closed), places.map { it.state })
            assertEquals(listOf(200, 300), places.dropLast(1).map { it.served })
            assertEquals(4, h.backend.callsTo(frontierLine))
            assertTrue(2_000L in h.delays, "the first retry waits 2 s: ${h.delays}")
        }

    @Test
    fun anOfflineBlipRecoversToo() =
        runTest {
            val h = QueueHarness()
            h.script(frontiers = listOf(open(100), Reply.offline(), open(130), closed))

            assertEquals(3, h.places().size)
        }

    @Test
    fun fiveFailuresInARowEndTheFlowWithBackoffGrowingTo16Seconds() =
        runTest {
            val h = QueueHarness()
            h.script(frontiers = listOf(open(100), unavailable()))

            val failure = assertFailsWith<DomainException> { h.places() }

            assertEquals(503, assertIs<DomainResult.Error>(failure.result).code)
            assertEquals(1 + 5, h.backend.callsTo(frontierLine))
            assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L), h.delays.takeLast(4))
        }

    @Test
    fun aSuccessResetsTheFailureCount() =
        runTest {
            val h = QueueHarness()
            val four = List(4) { unavailable() }
            h.script(frontiers = four + open(100) + four + closed)

            val places = h.places()

            assertEquals(listOf(QueueState.Open, QueueState.Closed), places.map { it.state })
        }

    @Test
    fun backoffIsCappedAt30Seconds() {
        val pacing = QueuePacing()
        assertEquals(
            listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L),
            (1..6).map { pacing.backoffMillis(it, null) },
        )
    }

    @Test
    fun retryAfterIsHonouredAndCapped() =
        runTest {
            val rateLimited = QueueHarness()
            rateLimited.script(
                frontiers = listOf(Reply.problem(429, "RATE_LIMITED", retryAfter = 20), open(100), closed),
            )
            rateLimited.places()
            assertTrue(20_000L in rateLimited.delays, "${rateLimited.delays}")

            val absurd = QueueHarness()
            absurd.script(frontiers = listOf(unavailable(retryAfter = 99_999), closed))
            absurd.places()
            assertTrue(120_000L in absurd.delays, "capped at two minutes: ${absurd.delays}")
        }

    @Test
    fun aFatalClientErrorEndsAtOnceWithoutRetrying() =
        runTest {
            listOf(401, 403, 404).forEach { status ->
                val h = QueueHarness()
                h.script(frontiers = listOf(Reply.problem(status, "FORBIDDEN")))

                val failure = assertFailsWith<DomainException> { h.places() }

                assertEquals(status, assertIs<DomainResult.Error>(failure.result).code)
                assertEquals(1, h.backend.callsTo(frontierLine), "status $status")
            }
        }

    // endregion

    // region: the claim

    @Test
    fun aTransientFailureDuringTheClaimIsRetriedInsideTheWindow() =
        runTest {
            val h = QueueHarness()
            h.script(frontiers = listOf(open(400)), claim = listOf(unavailable(), ok(ADMISSION_JSON)))

            val places = h.places()

            assertEquals(listOf(QueueState.Turn), places.map { it.state })
            assertEquals("mock-admission-token", h.tokens.getAdmissionToken())
            assertEquals(2, h.backend.callsTo(claimLine))
            assertTrue(2_000L in h.delays)
        }

    @Test
    fun claimFailuresInARowEndTheFlowButNotTheTurnWindowAlone() =
        runTest {
            val h = QueueHarness()
            h.script(frontiers = listOf(open(400)), claim = listOf(unavailable()))
            val seen = mutableListOf<QueuePlace>()

            val failure = assertFailsWith<DomainException> { h.queue.observePlace(EVT).collect { seen += it } }

            assertEquals(503, assertIs<DomainResult.Error>(failure.result).code)
            assertEquals(listOf(QueueState.Turn), seen.map { it.state })
            assertEquals(5, h.backend.callsTo(claimLine))
        }

    @Test
    fun aLongRetryAfterWaitsOutTheWindowThenMakesOneLastAttempt() =
        runTest {
            val h = QueueHarness()
            h.script(
                frontiers = listOf(open(400)),
                claim = listOf(Reply.problem(409, "NOT_YET", retryAfter = 120)),
            )

            val places = h.places()

            assertEquals(listOf(QueueState.Turn, QueueState.Missed), places.map { it.state })
            assertEquals(listOf(60_000L), h.delays, "the wait is cut to the window, not skipped")
            assertEquals(2, h.backend.callsTo(claimLine), "one attempt at the start and one at the end of the window")
        }

    @Test
    fun theLastAttemptCanStillSucceed() =
        runTest {
            val h = QueueHarness()
            h.script(
                frontiers = listOf(open(400)),
                claim = listOf(Reply.problem(409, "NOT_YET", retryAfter = 120), ok(ADMISSION_JSON)),
            )

            val places = h.places()

            assertEquals(listOf(QueueState.Turn), places.map { it.state })
            assertEquals("mock-admission-token", h.tokens.getAdmissionToken())
        }

    @Test
    fun aSessionProblemOnClaimAsksForSignInButADeviceProblemRegistersAgain() =
        runTest {
            val device = QueueHarness()
            device.script(
                frontiers = listOf(open(400)),
                claim = listOf(Reply.problem(401, "FORBIDDEN"), ok(ADMISSION_JSON)),
            )

            device.places()

            assertEquals(
                2,
                device.backend.callsTo("POST /queue/devices"),
                "registered again after the rejected device token",
            )
            assertEquals(2, device.backend.callsTo(claimLine))

            val stubborn = QueueHarness()
            stubborn.script(frontiers = listOf(open(400)), claim = listOf(Reply.problem(401, "FORBIDDEN")))
            val failure = assertFailsWith<DomainException> { stubborn.places() }
            assertEquals(401, assertIs<DomainResult.Error>(failure.result).code)
            assertEquals(2, stubborn.backend.callsTo("POST /queue/devices"), "only once")
            assertEquals(2, stubborn.backend.callsTo(claimLine))
        }

    // endregion

    // region: the unsynced clock

    @Test
    fun anUnsyncedClockNeverTimesAPlace() =
        runTest {
            // The clock counts from a small arbitrary start; the frontier's own ts is the only server time.
            val h = QueueHarness(synced = false, clockStart = 5_000L)
            h.script(frontiers = listOf(open(100), open(400)))

            val places = h.places()

            val turn = places.last()
            assertEquals(QueueState.Turn, turn.state)
            assertTrue(
                places.all { it.serverNowEpochMillis == FIXTURE_NOW_MILLIS },
                "${places.map { it.serverNowEpochMillis }}",
            )
            assertEquals(FIXTURE_NOW_MILLIS + 60_000L, turn.turnExpiresAtEpochMillis)
        }

    @Test
    fun aSyncedClockIsUsedForTheSnapshotTime() =
        runTest {
            val h = QueueHarness(clockStart = FIXTURE_NOW_MILLIS + 7_500L)
            h.script(frontiers = listOf(open(100), closed))

            assertEquals(FIXTURE_NOW_MILLIS + 7_500L, h.places().first().serverNowEpochMillis)
        }

    // endregion

    // region: concurrency and leaving

    @Test
    fun concurrentForcedRefreshesRegisterOnce() =
        runTest {
            val h = QueueHarness()
            h.backend.on("POST /queue/devices", Reply.created(DEVICE_JSON))
            h.backend.onRequest = { delay(50) }

            val both =
                listOf(
                    async { h.devices.ensureRegistered(forceRefresh = true) },
                    async { h.devices.ensureRegistered(forceRefresh = true) },
                )

            assertEquals(listOf(DomainResult.Success(Unit), DomainResult.Success(Unit)), both.map { it.await() })
            assertEquals(1, h.backend.callsTo("POST /queue/devices"))
        }

    @Test
    fun leavingAlsoClearsTheAdmission() =
        runTest {
            val h = QueueHarness()
            h.script(frontiers = listOf(open(400)))
            h.backend.on("DELETE /queue/$EVT/me", Reply.noContent())
            h.places()
            assertEquals("mock-admission-token", h.tokens.getAdmissionToken())

            assertEquals(DomainResult.Success(Unit), h.queue.leave(EVT))

            assertNull(h.tokens.getAdmissionToken())
            assertNull(h.queueCache.getAdmission())
            assertNull(h.tokens.getQueueToken(EVT))
        }

    @Test
    fun leavingAnotherEventKeepsTheAdmission() =
        runTest {
            val h = QueueHarness()
            h.script(frontiers = listOf(open(400)))
            h.places()
            h.tokens.setQueueToken("evt_other", "t")
            h.backend.on("DELETE /queue/evt_other/me", Reply.noContent())

            h.queue.leave("evt_other")

            assertEquals("mock-admission-token", h.tokens.getAdmissionToken())
        }

    // endregion

    private companion object {
        const val EVT = "evt_samudra"
    }
}
