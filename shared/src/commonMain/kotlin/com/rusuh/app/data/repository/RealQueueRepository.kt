package com.rusuh.app.data.repository

import com.rusuh.app.data.cache.QueueCache
import com.rusuh.app.data.clock.parseRfc3339Millis
import com.rusuh.app.data.network.LocaleProvider
import com.rusuh.app.data.remote.EventApiService
import com.rusuh.app.data.remote.QueueApiService
import com.rusuh.app.data.remote.dto.EventDto
import com.rusuh.app.data.remote.dto.FrontierDto
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * The real queue: [observePlace] joins (idempotent) and then polls the public frontier at the ADR-0003 cadence until
 * the flow collector is cancelled, the queue closes, or the turn is claimed.
 *
 * - Position is `seq - served`; the queue states map onto [QueueState] (`preopen`, `open`, `paused` with its reason,
 *   `closed` = sold out). A closed queue drops the stored queue token and the cached event id.
 * - At `served >= seq` the turn starts: a [QueueState.Turn] place is emitted at once (its 60 s window from `/me`
 *   `turnExpiresAt`, or from the server clock), then `claim` is retried (`NOT_YET`/`FULL`, about 1 s apart) inside
 *   that window. A 200 stores the admission token and re-emits the turn with the admission's own deadline; a 410
 *   `MISSED`/`EXPIRED` or running out of the window emits [QueueState.Missed]; a 401 fails the flow with
 *   [SignInRequiredException] (a guest cannot claim).
 * - Any other error, including a 404 or a network failure, fails the flow with [DomainException]; the Waiting Room
 *   keeps the last place and offers a retry.
 * - All times come from the [ServerClock], never the device clock.
 *
 * TODO(FR-04-M4 #30): the last 2,000 places also open the SSE stream (`streamFrom` of the frontier, ADR-0015);
 * polling stays the source of truth.
 */
@Suppress("LongParameterList") // the collaborators of one use case; each is a separate seam
class RealQueueRepository(
    queueApi: QueueApiService,
    eventApi: EventApiService,
    devices: DeviceRepository,
    private val tokens: TokenStore,
    private val cache: QueueCache,
    private val serverClock: ServerClock,
    private val localeProvider: LocaleProvider,
    private val pacing: QueuePacing = QueuePacing(),
) : QueueRepository {
    private val calls = QueueCalls(queueApi, eventApi, devices, tokens, cache)

    override fun observePlace(eventId: String): Flow<QueuePlace> =
        if (eventId.isBlank()) {
            emptyFlow()
        } else {
            flow { Poller(eventId, this).run() }
        }

    override suspend fun leave(eventId: String): DomainResult<Unit> {
        val token = tokens.getQueueToken(eventId)
        return if (token == null) {
            cache.removeJoinedEvent(eventId)
            DomainResult.Success(Unit)
        } else {
            calls.leave(eventId, token).also { if (it is DomainResult.Success) calls.forget(eventId) }
        }
    }

    /** The server time of a snapshot: the synced clock, else the frontier's own `ts`. */
    private fun serverNow(frontier: FrontierDto): Long =
        serverClock.nowMillis().takeIf { it > 0L } ?: frontier.tsMillis() ?: 0L

    /** One `observePlace` collection: the state of a single join-and-poll run. */
    private inner class Poller(
        private val eventId: String,
        private val out: FlowCollector<QueuePlace>,
    ) {
        private var event: EventDto? = null
        private var info = QueueEventInfo(title = eventId)
        private var seq: Int? = null
        private var position: Int? = null
        private var opensAtFromPlace: Long? = null

        suspend fun run() {
            val joined = calls.join(eventId)
            seq = joined.seq
            opensAtFromPlace = joined.opensAt?.let(::parseRfc3339Millis)
            event = calls.event(eventId)
            event?.let { info = it.toQueueEventInfo(localeProvider.language()) }
            var finished = false
            while (!finished) {
                finished = step(calls.frontier(eventId))
                if (!finished) pacing.delay(pacing.pollDelayMillis(position))
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
            val mine = seq ?: fetchSeqAfterDraw()
            return when {
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

        /** After the pre-sale draw the number comes from `/me` once, 0-30 s after the queue opens (spreads load). */
        private suspend fun fetchSeqAfterDraw(): Int? {
            pacing.delay(pacing.randomUpTo(ME_JITTER_MILLIS))
            return (calls.me(eventId, lenient = false) as? MeResult.Found)?.place?.seq?.also { seq = it }
        }

        /** The turn. Returns true when the run is over; false when the claim says to go back to waiting (NO_STOCK). */
        private suspend fun onTurn(frontier: FrontierDto): Boolean =
            when (val me = calls.me(eventId, lenient = true)) {
                MeResult.Missed -> end(QueueState.Missed, frontier)
                MeResult.Closed -> end(QueueState.Closed, frontier)
                is MeResult.Found, MeResult.Unavailable -> {
                    // The 60 s window is only a display hint; the claim answers for itself.
                    val shown = (me as? MeResult.Found)?.place?.turnExpiresAt?.let(::parseRfc3339Millis)
                    val turn =
                        place(QueueState.Turn, frontier)
                            .copy(turnExpiresAtEpochMillis = shown ?: (serverNow(frontier) + TURN_WINDOW_MILLIS))
                    out.emit(turn)
                    claim(frontier, turn)
                }
            }

        /** Retries the claim inside the 60 s window. Returns true when the run is over, false for NO_STOCK. */
        private suspend fun claim(
            frontier: FrontierDto,
            turn: QueuePlace,
        ): Boolean {
            var waited = 0L
            var over: Boolean? = null
            while (over == null) {
                when (val step = calls.claim(eventId)) {
                    is ClaimStep.Admitted -> {
                        val expiresAt = step.admission.expiresAt?.let(::parseRfc3339Millis)
                        out.emit(turn.copy(turnExpiresAtEpochMillis = expiresAt ?: turn.turnExpiresAtEpochMillis))
                        over = true
                    }
                    is ClaimStep.Retry -> {
                        val wait = claimRetryMillis(step)
                        if (waited + wait > TURN_WINDOW_MILLIS) {
                            over = end(QueueState.Missed, frontier)
                        } else {
                            pacing.delay(wait)
                            waited += wait
                        }
                    }
                    ClaimStep.BackToWaiting -> over = false
                    ClaimStep.Missed -> over = end(QueueState.Missed, frontier)
                    ClaimStep.Closed -> over = end(QueueState.Closed, frontier)
                }
            }
            return checkNotNull(over)
        }

        /** `NOT_YET`: about 1 s. `FULL`: 1-2 s. Both with jitter; a `Retry-After` hint replaces the 1 s. */
        private fun claimRetryMillis(retry: ClaimStep.Retry): Long {
            val base = (retry.afterSeconds?.takeIf { it > 0 } ?: 1) * CLAIM_RETRY_MILLIS
            return if (retry.code == ErrorCode.FULL) {
                base + pacing.randomUpTo(CLAIM_RETRY_MILLIS)
            } else {
                pacing.jittered(base)
            }
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
        const val CLAIM_RETRY_MILLIS = 1_000L
        const val ME_JITTER_MILLIS = 30_000L
    }
}
