package com.rusuh.app.data.clock

import com.rusuh.app.data.network.plugin.findServerNow
import com.rusuh.app.data.network.plugin.testHttp
import com.rusuh.app.domain.clock.remainingMillis
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

@OptIn(ExperimentalTime::class)
class ServerClockTest {
    // 2026-11-14T10:40:00Z
    private val serverNow = 1_794_652_800_000L

    private val time = TestTimeSource()
    private val clock = MonotonicServerClock(timeSource = time)

    @Test
    fun parsesStringDateToEpochMillis() {
        assertEquals(0L, parseStringDateMillis("1970-01-01T00:00:00Z"))
        assertEquals(serverNow, parseStringDateMillis("2026-11-14T10:40:00Z"))
        assertEquals(serverNow + 250, parseStringDateMillis("2026-11-14T10:40:00.25Z"))
        assertEquals(serverNow, parseStringDateMillis("2026-11-14T17:40:00+07:00"))
        assertEquals(951_782_400_000L, parseStringDateMillis("2000-02-29T00:00:00Z"))
        assertNull(parseStringDateMillis("2026-13-14T10:40:00Z"))
        assertNull(parseStringDateMillis("yesterday"))
        assertNull(parseStringDateMillis("2026-11-14"))
    }

    @Test
    fun clockAdvancesMonotonicallyFromTheRecordedServerTime() {
        clock.recordServerNow(serverNow)
        time += 1500.milliseconds

        assertEquals(serverNow + 1500, clock.nowMillis())
        assertTrue(clock.isSynced)
    }

    @Test
    fun unsyncedClockCountsFromItsStartValue() {
        val unsynced = MonotonicServerClock(serverEpochMillisAtStart = 1_000L, timeSource = time)
        time += 2.seconds

        assertEquals(3_000L, unsynced.nowMillis())
        assertFalse(unsynced.isSynced)
    }

    @Test
    fun laterResponseCorrectsDrift() {
        clock.recordServerNow(serverNow)
        time += 10.seconds
        // The server says 10.5 s passed while the device counted 10 s: the clock follows the server.
        clock.recordServerNow(serverNow + 10_500)

        assertEquals(serverNow + 10_500, clock.nowMillis())
        time += 1.seconds
        assertEquals(serverNow + 11_500, clock.nowMillis())
    }

    @Test
    fun remainingCountsDownAndNeverGoesNegative() {
        clock.recordServerNow(serverNow)
        val expiresAt = serverNow + 5_000

        assertEquals(5_000L, clock.remainingMillis(expiresAt))
        time += 3.seconds
        assertEquals(2_000L, clock.remainingMillis(expiresAt))
        time += 10.seconds
        assertEquals(0L, clock.remainingMillis(expiresAt))
        assertEquals(0L, clock.remainingMillis(serverNow - 1_000_000))
    }

    @Test
    fun findsServerNowAtTheTopLevelOrInANestedObject() {
        assertEquals(serverNow, findServerNow("{\"serverNow\":\"2026-11-14T10:40:00Z\",\"expiresAt\":\"x\"}"))
        assertEquals(serverNow, findServerNow("{\"booking\":{\"serverNow\":\"2026-11-14T10:40:00Z\"}}"))
        assertNull(findServerNow("{\"serverNow\":\"not a date\"}"))
        assertNull(findServerNow("{\"serverNow\":12}"))
        assertNull(findServerNow("[1,2,3]"))
        assertNull(findServerNow("{\"other\":1}"))
    }

    @Test
    fun responsesRecordServerNowAndDriftIsCorrectedBetweenTwoResponses() =
        runTest {
            val bodies = ArrayDeque(listOf("2026-11-14T10:40:00Z", "2026-11-14T10:40:10.500Z"))
            val http =
                testHttp(serverTime = clock) {
                    respond(
                        "{\"expiresAt\":\"2026-11-14T10:45:00Z\",\"serverNow\":\"${bodies.removeFirst()}\"}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            http.api.get<Map<String, String>>("/bookings/1")
            time += 10.seconds
            assertEquals(serverNow + 10_000, clock.nowMillis())
            http.api.get<Map<String, String>>("/bookings/1")

            assertEquals(serverNow + 10_500, clock.nowMillis())
        }

    @Test
    fun responsesWithoutServerNowOrOfOtherTypesLeaveTheClockAlone() =
        runTest {
            val http =
                testHttp(serverTime = clock) { request ->
                    if (request.url.encodedPath == "/plain") {
                        respond("{\"a\":1}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    } else {
                        respond(
                            "{\"serverNow\":\"2026-11-14T10:40:00Z\"}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "text/plain"),
                        )
                    }
                }

            http.api.get<Map<String, Int>>("/plain")
            http.api.get<String>("/text")

            assertFalse(clock.isSynced)
        }

    @Test
    fun problemResponsesAreNotInspected() =
        runTest {
            val http =
                testHttp(serverTime = clock) {
                    respond(
                        "{\"status\":410,\"code\":\"EXPIRED\",\"serverNow\":\"2026-11-14T10:40:00Z\"}",
                        HttpStatusCode.Gone,
                        headersOf(HttpHeaders.ContentType, "application/problem+json"),
                    )
                }

            http.api.get<Unit>("/bookings/1")

            // problem+json is not application/json: ignored on purpose, only success shapes carry serverNow.
            assertFalse(clock.isSynced)
        }

    @Test
    fun findsServerNowInTheRealSpecShapes() {
        val now = "\"serverNow\":\"2026-11-14T10:40:00Z\""
        val activeBookings = "{\"items\":[{\"bookingId\":\"bk_1\",\"status\":\"SELECTION\",$now}]}"
        val categories = "{\"eventId\":\"evt\",\"categories\":[{\"categoryId\":\"c\"}],\"maxTickets\":3,$now}"
        val queuePlace = "{\"eventId\":\"evt\",\"state\":\"open\",\"seq\":41260,$now}"
        val booking = "{\"bookingId\":\"bk_1\",\"attendees\":[{\"fullName\":\"A\"}],\"price\":{\"total\":1},$now}"

        listOf(
            activeBookings,
            categories,
            queuePlace,
            booking,
        ).forEach { assertEquals(serverNow, findServerNow(it), it) }
        assertNull(findServerNow("{\"items\":[]}"))
    }

    @Test
    fun anchorAddsHalfTheRoundTrip() {
        val sentAt = clock.markNow()
        time += 400.milliseconds

        clock.recordServerNow(serverNow, sentAt)

        assertEquals(serverNow + 200, clock.nowMillis())
        time += 1.seconds
        assertEquals(serverNow + 1_200, clock.nowMillis())
    }

    @Test
    fun lateResponseOfAnOlderRequestNeverMovesTheClockBackwards() {
        val slowSentAt = clock.markNow()
        time += 1.seconds
        val freshSentAt = clock.markNow()
        time += 100.milliseconds
        clock.recordServerNow(serverNow + 1_050, freshSentAt)
        time += 500.milliseconds
        val before = clock.nowMillis()

        // The slow response was produced at serverNow + 0 but arrives last.
        clock.recordServerNow(serverNow, slowSentAt)

        assertEquals(before, clock.nowMillis())
    }

    @Test
    fun outOfOrderResponsesThroughTheClientKeepTheFreshAnchor() =
        runTest {
            val slowGate = CompletableDeferred<Unit>()
            val slowEntered = CompletableDeferred<Unit>()
            val http =
                testHttp(serverTime = clock) { request ->
                    val stamp =
                        if (request.url.encodedPath == "/slow") {
                            slowEntered.complete(Unit)
                            slowGate.await()
                            "2026-11-14T10:40:00Z"
                        } else {
                            "2026-11-14T10:40:01Z"
                        }
                    respond(
                        "{\"serverNow\":\"$stamp\"}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val slow = async { http.api.get<Map<String, String>>("/slow") }
            slowEntered.await()
            time += 1.seconds
            http.api.get<Map<String, String>>("/fresh")
            time += 500.milliseconds
            slowGate.complete(Unit)
            slow.await()

            assertEquals(serverNow + 1_500, clock.nowMillis())
        }

    @Test
    fun halfRoundTripIsAppliedToClientResponses() =
        runTest {
            val http =
                testHttp(serverTime = clock) {
                    time += 400.milliseconds
                    respond(
                        "{\"serverNow\":\"2026-11-14T10:40:00Z\"}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            http.api.get<Map<String, String>>("/bookings/1")

            assertEquals(serverNow + 200, clock.nowMillis())
        }

    @Test
    fun bodiesOverTheCapAreSkippedEvenWithoutContentLength() =
        runTest {
            val padding = "x".repeat(300 * 1024)
            val http =
                testHttp(serverTime = clock) {
                    respond(
                        "{\"pad\":\"$padding\",\"serverNow\":\"2026-11-14T10:40:00Z\"}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            http.api.get<Map<String, String>>("/big")

            assertFalse(clock.isSynced)
        }
}
