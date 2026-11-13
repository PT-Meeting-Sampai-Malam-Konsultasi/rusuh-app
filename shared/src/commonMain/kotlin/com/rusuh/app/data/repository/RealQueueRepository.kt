package com.rusuh.app.data.repository

import com.rusuh.app.data.cache.QueueCache
import com.rusuh.app.data.clock.parseRfc3339Millis
import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.network.LocaleProvider
import com.rusuh.app.data.remote.EventApiService
import com.rusuh.app.data.remote.QueueApiService
import com.rusuh.app.data.remote.dto.EventDto
import com.rusuh.app.data.remote.dto.FrontierDto
import com.rusuh.app.data.remote.dto.QueueTurnDto
import com.rusuh.app.data.remote.mapper.QueueWire
import com.rusuh.app.data.remote.mapper.localMoment
import com.rusuh.app.data.remote.mapper.opensAtMillis
import com.rusuh.app.data.remote.mapper.pauseReason
import com.rusuh.app.data.remote.mapper.time
import com.rusuh.app.data.remote.mapper.toQueueEventInfo
import com.rusuh.app.data.remote.mapper.tsMillis
import com.rusuh.app.data.remote.mapper.words
import com.rusuh.app.data.remote.mapper.zoneLabelFor
import com.rusuh.app.data.storage.TokenStore
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.DomainException
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import com.rusuh.app.domain.model.SignInRequiredException
import com.rusuh.app.domain.repository.DeviceRepository
import com.rusuh.app.domain.repository.QueueRepository
import com.rusuh.app.feature.waitingroom.domain.model.QueueEventInfo
import com.rusuh.app.feature.waitingroom.domain.model.QueuePlace
import com.rusuh.app.feature.waitingroom.domain.model.QueueState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * The real queue: [observePlace] joins (idempotent) and then polls the public frontier at the ADR-0003 cadence until
 * the flow collector is cancelled, the queue closes, or the turn is claimed.
 *
 * - Position is `seq - served`; the queue states map onto [QueueState] (`preopen`, `open`, `paused` with its reason,
 *   `closed` = sold out). A closed queue drops the stored queue token and the cached event id.
 * - At `served >= seq` the turn starts: a [QueueState.Turn] place is emitted at once (its 60 s window from `/me`
 *   `turnExpiresAt`, or from the server clock), then `claim` is retried (`NOT_YET`/`FULL`, about 1 s apart) inside
 *   that window. A 200 stores the admission token and its non-secret details (`AdmissionInfo`, for Detail) and ends
 *   the flow; the YourTurn countdown stays the 60 s window (the admission's longer order window belongs to
 *   Detail); a 410
 *   `MISSED`/`EXPIRED` or running out of the window emits [QueueState.Missed]; a 401 with code `UNAUTHORIZED` fails the
 *   flow with [SignInRequiredException] (a guest cannot claim), any other 401 registers the device again once.
 * - Near the front (position <= streamFrom), opens an SSE stream for real-time updates (ADR-0015). Polling is the
 *   fallback and source of truth; the stream is closed on cancellation or when position goes back above streamFrom.
 * - Transient failures (network, 408, 429, 5xx) inside the loop are retried with capped exponential backoff that
 *   honours `Retry-After`; the flow fails with [DomainException] after 5 failures in a row, or at once on a failure
 *   that waiting cannot fix (401/403/404 where they are not meaningful). The Waiting Room keeps the last place and
 *   offers a retry.
 * - All times come from the [ServerClock], never the device clock.
 */
@Suppress("LongParameterList") // the collaborators of one use case; each is a separate seam
class RealQueueRepository(
    private val queueApiService: QueueApiService,
    eventApi: EventApiService,
    devices: DeviceRepository,
    private val tokens: TokenStore,
    private val cache: QueueCache,
    private val serverClock: ServerClock,
    private val localeProvider: LocaleProvider,
    private val pacing: QueuePacing = QueuePacing(),
    private val json: Json = ApiClient.defaultJson,
    private val streamOpener: suspend (QueueStreamParams) -> StreamOutcome = ::openQueueStream,
) : QueueRepository {
    private val calls = QueueCalls(queueApiService, eventApi, devices, tokens, cache)

    override fun observePlace(eventId: String): Flow<QueuePlace> =
        if (eventId.isBlank()) {
            emptyFlow()
        } else {
            flow { Poller(eventId, this).run() }
        }

    override suspend fun leave(eventId: String): DomainResult<Unit> {
        val token = tokens.getQueueToken(eventId)
        val result = if (token == null) DomainResult.Success(Unit) else calls.leave(eventId, token)
        if (result is DomainResult.Success) {
            calls.forget(eventId)
            calls.forgetAdmissionOf(eventId)
        }
        return result
    }

    /**
     * The server time of a snapshot: the synced clock, else the frontier's own `ts` (Unix seconds). An unsynced clock
     * counts from an arbitrary start, so it is never used.
     */
    private fun serverNow(frontier: FrontierDto): Long =
        if (serverClock.isSynced) serverClock.nowMillis() else frontier.tsMillis() ?: 0L

    /** One `observePlace` collection: the state of a single join-and-poll run. */
    @Suppress("TooManyFunctions")
    private inner class Poller(
        private val eventId: String,
        private val out: FlowCollector<QueuePlace>,
    ) {
        private var event: EventDto? = null
        private var info = QueueEventInfo(title = eventId)
        private var seq: Int? = null
        private var position: Int? = null
        private var streamFrom: Int? = null
        private var opensAtFromPlace: Long? = null
        private var drawJitterDone = false
        private var failures = 0
        private var lastFrontier: FrontierDto? = null

        // SSE state
        private var streamConnectFailures = 0
        private var nextStreamRetryAt: Long = 0L
        private var lastEventId: String? = null
        private var serverRetryMillis: Long = QueuePacing.SSE_DEFAULT_RETRY_MILLIS

        // Claim guard: only one claim attempt at a time across both transports
        private val claimMutex = Mutex()
        private var claimStarted = false

        suspend fun run() {
            val joined = calls.join(eventId)
            seq = joined.seq
            opensAtFromPlace = joined.opensAt?.let(::parseRfc3339Millis)
            event = calls.event(eventId)
            event?.let { info = it.toQueueEventInfo(localeProvider.language()) }
            var finished = false
            while (!finished) {
                val frontier = resilient { calls.frontier(eventId) }
                lastFrontier = frontier
                streamFrom = frontier.streamFrom
                finished = step(frontier)
                if (!finished) {
                    if (shouldStream(frontier)) {
                        finished = runStream(frontier)
                    } else {
                        pacing.delay(pacing.pollDelayMillis(position))
                    }
                }
            }
        }

        @Suppress("ReturnCount") // early returns simplify the logic
        private fun shouldStream(frontier: FrontierDto): Boolean {
            val sf = frontier.streamFrom ?: return false
            val pos = position ?: return false
            val isNearFront = sf > 0 && pos <= sf
            val canRetry = pacing.nowMillis() >= nextStreamRetryAt
            val notTooManyFailures = streamConnectFailures < QueuePacing.SSE_MAX_CONNECT_FAILURES
            val isOpenOrPaused = frontier.state == QueueWire.OPEN || frontier.state == QueueWire.PAUSED
            return isNearFront && canRetry && notTooManyFailures && isOpenOrPaused
        }

        private suspend fun runStream(initialFrontier: FrontierDto): Boolean {
            val queueToken = tokens.getQueueToken(eventId) ?: return false
            val params =
                QueueStreamParams(
                    api = queueApiService.api,
                    eventId = eventId,
                    queueToken = queueToken,
                    deviceToken = tokens.getDeviceToken(),
                    lastEventId = lastEventId,
                    json = json,
                )
            return when (val outcome = streamOpener(params)) {
                is StreamOutcome.Healthy -> consumeStream(outcome.events)
                StreamOutcome.TooFar -> {
                    streamConnectFailures = QueuePacing.SSE_MAX_CONNECT_FAILURES
                    false
                }
                is StreamOutcome.Closed -> handleStreamClosed(outcome.code, initialFrontier)
                is StreamOutcome.StreamFull -> {
                    val retryAfter =
                        outcome.retryAfterSeconds?.times(1000L) ?: QueuePacing.SSE_RETRY_DELAY_MILLIS
                    nextStreamRetryAt = pacing.nowMillis() + retryAfter
                    false
                }
                StreamOutcome.Unauthorized -> {
                    streamConnectFailures++
                    false
                }
                is StreamOutcome.Failed -> {
                    streamConnectFailures++
                    if (streamConnectFailures >= QueuePacing.SSE_MAX_CONNECT_FAILURES) {
                        nextStreamRetryAt = pacing.nowMillis() + QueuePacing.SSE_RETRY_DELAY_MILLIS
                    }
                    false
                }
            }
        }

        private suspend fun consumeStream(events: Flow<QueueStreamEvent>): Boolean =
            coroutineScope {
                val channel = events.produceIn(this)
                var first = true
                try {
                    while (true) {
                        val timeout =
                            if (first) {
                                QueuePacing.SSE_FIRST_EVENT_TIMEOUT_MILLIS
                            } else {
                                QueuePacing.SSE_SILENCE_TIMEOUT_MILLIS
                            }
                        val item = withTimeoutOrNull(timeout) { channel.receiveCatching() }
                        if (item == null) {
                            if (first) {
                                onStreamFailure()
                            } else {
                                streamConnectFailures = 0
                                nextStreamRetryAt = pacing.nowMillis() + QueuePacing.SSE_RETRY_DELAY_MILLIS
                            }
                            return@coroutineScope false
                        }
                        if (item.isClosed) {
                            val cause = item.exceptionOrNull()
                            if (cause != null) {
                                nextStreamRetryAt = pacing.nowMillis() + serverRetryMillis
                                return@coroutineScope false
                            }
                            if (!first) {
                                streamConnectFailures = 0
                                nextStreamRetryAt = pacing.nowMillis() + serverRetryMillis
                            } else {
                                onStreamFailure()
                            }
                            return@coroutineScope false
                        }
                        val event = item.getOrThrow()
                        first = false
                        streamConnectFailures = 0
                        if (handleStreamEvent(event)) return@coroutineScope true
                        if (position != null && (position ?: 0) > (streamFrom ?: 0)) return@coroutineScope false
                    }
                    @Suppress("UNREACHABLE_CODE")
                    false
                } finally {
                    channel.cancel()
                }
            }

        private suspend fun handleStreamEvent(event: QueueStreamEvent): Boolean =
            when (event) {
                is QueueStreamEvent.Frontier -> {
                    lastEventId = event.dto.ts?.toString()
                    event.dto.streamFrom?.let { streamFrom = it }
                    handleStreamFrontier(event.dto)
                }
                is QueueStreamEvent.Turn -> handleStreamTurn(event.dto)
                QueueStreamEvent.Heartbeat -> false
                is QueueStreamEvent.RetryHint -> {
                    serverRetryMillis =
                        event.millis.coerceIn(
                            QueuePacing.SSE_DEFAULT_RETRY_MILLIS,
                            QueuePacing.BACKOFF_CAP_MILLIS,
                        )
                    false
                }
            }

        private fun onStreamFailure() {
            streamConnectFailures++
            if (streamConnectFailures >= QueuePacing.SSE_MAX_CONNECT_FAILURES) {
                nextStreamRetryAt = pacing.nowMillis() + QueuePacing.SSE_RETRY_DELAY_MILLIS
            }
        }

        private suspend fun handleStreamFrontier(frontier: FrontierDto): Boolean {
            lastFrontier = frontier
            return step(frontier)
        }

        @Suppress("ReturnCount")
        private suspend fun handleStreamTurn(turn: QueueTurnDto): Boolean {
            val frontier = lastFrontier ?: return false
            if (!acquireClaim()) return false
            val turnExpiresAt =
                turn.turnExpiresAt?.let(::parseRfc3339Millis) ?: (serverNow(frontier) + TURN_WINDOW_MILLIS)
            val turnPlace =
                place(QueueState.Turn, frontier).copy(turnExpiresAtEpochMillis = turnExpiresAt)
            out.emit(turnPlace)
            return claimAndRelease(frontier)
        }

        private suspend fun handleStreamClosed(
            code: ErrorCode,
            frontier: FrontierDto,
        ): Boolean {
            return when (code) {
                ErrorCode.QUEUE_CLOSED -> end(QueueState.Closed, frontier)
                ErrorCode.MISSED, ErrorCode.EXPIRED -> end(QueueState.Missed, frontier)
                else -> end(QueueState.Closed, frontier)
            }
        }

        /** Handles one frontier. Returns true when the run is over (closed, claimed, missed). */
        private suspend fun step(frontier: FrontierDto): Boolean =
            when (frontier.state) {
                QueueWire.CLOSED -> end(QueueState.Closed, frontier)
                QueueWire.PREOPEN -> {
                    out.emit(preOpen(frontier))
                    false
                }
                QueueWire.OPEN, QueueWire.PAUSED -> openOrPaused(frontier)
                else -> throw DomainException(DomainResult.TechnicalError)
            }

        private suspend fun openOrPaused(frontier: FrontierDto): Boolean {
            val over = if (seq == null) drawNumber(frontier) else null
            val mine = seq
            return when {
                over != null -> over
                mine == null -> false
                frontier.state == QueueWire.PAUSED -> {
                    position = mine - frontier.served
                    out.emit(place(QueueState.Paused, frontier).copy(pauseReason = frontier.pauseReason()))
                    false
                }
                frontier.served >= mine -> onTurn(frontier)
                else -> {
                    position = mine - frontier.served
                    out.emit(place(QueueState.Open, frontier))
                    false
                }
            }
        }

        /**
         * After the pre-sale draw the number comes from `/me`, after one random 0-30 s wait per draw (spreads the
         * load). Returns null once the number is known; otherwise whether the run is over (false: ask again at the
         * next poll, e.g. a 404 or a null `seq` means "not drawn yet").
         */
        private suspend fun drawNumber(frontier: FrontierDto): Boolean? {
            if (!drawJitterDone) {
                drawJitterDone = true
                pacing.delay(pacing.randomUpTo(ME_JITTER_MILLIS))
            }
            return when (val me = resilient { calls.me(eventId, lenient = false) }) {
                is MeResult.Found -> {
                    seq = me.place.seq
                    if (seq == null) false else null
                }
                MeResult.Missed -> end(QueueState.Missed, frontier)
                MeResult.Closed -> end(QueueState.Closed, frontier)
                MeResult.NotFound, MeResult.Unavailable -> false
            }
        }

        /**
         * Runs [call], retrying a transient failure (network, 408, 429, 5xx) with capped exponential backoff that
         * honours `Retry-After`. It fails after [QueuePacing.MAX_CONSECUTIVE_FAILURES] failures in a row, on a failure
         * that waiting cannot fix, or once [deadline] (monotonic millis) has passed. Any success resets the count.
         */
        private suspend fun <T> resilient(
            deadline: Long? = null,
            call: suspend () -> T,
        ): T {
            while (true) {
                try {
                    return call().also { failures = 0 }
                } catch (failure: DomainException) {
                    val now = pacing.nowMillis()
                    failures++
                    if (!failure.isTransient() || failures >= QueuePacing.MAX_CONSECUTIVE_FAILURES) throw failure
                    if (deadline != null && now >= deadline) throw failure
                    val wait = pacing.backoffMillis(failures, failure.retryAfterSeconds())
                    pacing.delay(if (deadline == null) wait else minOf(wait, deadline - now))
                }
            }
        }

        private suspend fun acquireClaim(): Boolean =
            claimMutex.withLock {
                if (claimStarted) {
                    false
                } else {
                    claimStarted = true
                    true
                }
            }

        /** One claim at a time across both transports; a claim that goes back to waiting (NO_STOCK) can run again. */
        private suspend fun claimAndRelease(frontier: FrontierDto): Boolean {
            val over = claim(frontier)
            if (!over) claimMutex.withLock { claimStarted = false }
            return over
        }

        /** The turn. Returns true when the run is over; false when the claim says to go back to waiting (NO_STOCK). */
        private suspend fun onTurn(frontier: FrontierDto): Boolean {
            if (!acquireClaim()) return false
            return when (val me = calls.me(eventId, lenient = true)) {
                MeResult.Missed -> end(QueueState.Missed, frontier)
                MeResult.Closed -> end(QueueState.Closed, frontier)
                is MeResult.Found, MeResult.NotFound, MeResult.Unavailable -> {
                    val shown = (me as? MeResult.Found)?.place?.turnExpiresAt?.let(::parseRfc3339Millis)
                    val turn =
                        place(QueueState.Turn, frontier)
                            .copy(turnExpiresAtEpochMillis = shown ?: (serverNow(frontier) + TURN_WINDOW_MILLIS))
                    out.emit(turn)
                    claimAndRelease(frontier)
                }
            }
        }

        /**
         * Claims inside the 60 s turn window, measured on the monotonic clock from the first attempt (so request time
         * counts). `NOT_YET`/`FULL` wait and retry, a transient failure backs off, a device-token 401 registers the
         * device again once. When a wait would pass the end of the window it is cut to the window and ONE last attempt
         * is made; only a failed last attempt is Missed. Returns true when the run is over, false for NO_STOCK.
         */
        private suspend fun claim(frontier: FrontierDto): Boolean {
            val deadline = pacing.nowMillis() + TURN_WINDOW_MILLIS
            var lastAttempt = false
            var reRegistered = false
            var over: Boolean? = null
            while (over == null) {
                when (val step = resilient(deadline) { calls.claim(eventId) }) {
                    is ClaimStep.Admitted -> over = true
                    is ClaimStep.Retry ->
                        if (lastAttempt) {
                            over = end(QueueState.Missed, frontier)
                        } else {
                            val full = step.code == ErrorCode.FULL
                            lastAttempt = pacing.waitForClaimRetry(full, step.afterSeconds, deadline)
                        }
                    is ClaimStep.DeviceRejected -> {
                        if (reRegistered) throw step.error
                        calls.reRegisterDevice()
                        reRegistered = true
                    }
                    ClaimStep.BackToWaiting -> over = false
                    ClaimStep.Missed -> over = end(QueueState.Missed, frontier)
                    ClaimStep.Closed -> over = end(QueueState.Closed, frontier)
                }
            }
            return checkNotNull(over)
        }

        /** Emits the final [state] (missed or closed), forgets the place and ends the run. */
        private suspend fun end(
            state: QueueState,
            frontier: FrontierDto,
        ): Boolean {
            calls.forget(eventId)
            out.emit(place(state, frontier))
            return true
        }

        private fun place(
            state: QueueState,
            frontier: FrontierDto,
        ) = QueuePlace(
            eventId = eventId,
            event = info,
            state = state,
            seq = seq ?: 0,
            served = frontier.served,
            serverNowEpochMillis = serverNow(frontier),
            ratePerMinute = frontier.ratePerMin?.takeIf { it > 0 },
        )

        private fun preOpen(frontier: FrontierDto): QueuePlace {
            val opensAt = frontier.opensAtMillis() ?: opensAtFromPlace
            val zone = zoneLabelFor(event?.timezone.orEmpty())
            val label = opensAt?.let { "${localMoment(it, zone).time(localeProvider.language().words())} ${zone.name}" }
            return place(QueueState.PreOpen, frontier)
                .copy(opensAtEpochMillis = opensAt, opensAtLabel = label.orEmpty(), ratePerMinute = null)
        }
    }

    private companion object {
        const val TURN_WINDOW_MILLIS = 60_000L
        const val ME_JITTER_MILLIS = 30_000L
        const val WATCHDOG_CHECK_INTERVAL_MILLIS = 1_000L
    }
}
