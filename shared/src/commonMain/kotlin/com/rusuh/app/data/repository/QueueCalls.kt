package com.rusuh.app.data.repository

import com.rusuh.app.data.cache.AdmissionInfo
import com.rusuh.app.data.cache.QueueCache
import com.rusuh.app.data.clock.parseStringDateMillis
import com.rusuh.app.data.network.ApiResult
import com.rusuh.app.data.remote.EventApiService
import com.rusuh.app.data.remote.QueueApiService
import com.rusuh.app.data.remote.dto.AdmissionDto
import com.rusuh.app.data.remote.dto.EventDto
import com.rusuh.app.data.remote.dto.FrontierDto
import com.rusuh.app.data.remote.dto.QueuePlaceDto
import com.rusuh.app.data.remote.mapper.QueueWire
import com.rusuh.app.data.storage.TokenStore
import com.rusuh.app.domain.model.DomainException
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import com.rusuh.app.domain.model.SignInRequiredException
import com.rusuh.app.domain.repository.DeviceRepository

/** What `GET /queue/{eventId}/me` told us. */
internal sealed interface MeResult {
    data class Found(val place: QueuePlaceDto) : MeResult

    /** 410 `MISSED`, `EXPIRED` or `MOVED`: this place is over, join again for a new number. */
    data object Missed : MeResult

    data object Closed : MeResult

    /** 404: no place yet (the pre-sale draw has not given a number). Not an error for the poll. */
    data object NotFound : MeResult

    /** The call failed and the caller did not need it (lenient mode). */
    data object Unavailable : MeResult
}

/** The next move after one `claim` attempt. */
internal sealed interface ClaimStep {
    data class Admitted(val admission: AdmissionDto) : ClaimStep

    /** `NOT_YET` or `FULL`: try again after [afterSeconds] (the `Retry-After` hint, or null for the default). */
    data class Retry(val code: ErrorCode, val afterSeconds: Int?) : ClaimStep

    /** `NO_STOCK`: every unit is held, go back to the waiting room. */
    data object BackToWaiting : ClaimStep

    /** 401 with a code other than `UNAUTHORIZED`: the device token was rejected, register again. */
    data class DeviceRejected(val error: DomainException) : ClaimStep

    data object Missed : ClaimStep

    data object Closed : ClaimStep
}

/**
 * The queue-related HTTP calls of [QueueRepositoryImpl] with their error policy: what each status means (device
 * re-registration on a rejected join, `MISSED` vs `QUEUE_CLOSED`, sign-in required on a claim), and which failures
 * throw [DomainException]. Holds no polling state.
 */
@Suppress("TooManyFunctions") // one function per endpoint plus its error policy
internal class QueueCalls(
    private val queueApi: QueueApiService,
    private val eventApi: EventApiService,
    private val devices: DeviceRepository,
    private val tokens: TokenStore,
    private val cache: QueueCache,
) {
    /**
     * Registers the device if needed, joins the queue (idempotent) and stores the queue token and the event id. A
     * 401 means the device token was rejected: register again once, then give up. A place that is already closed
     * stores nothing.
     */
    suspend fun join(eventId: String): QueuePlaceDto {
        ensureDevice(forceRefresh = false)
        var result = queueApi.join(eventId)
        if (result is ApiResult.Error && result.code == HTTP_UNAUTHORIZED) {
            ensureDevice(forceRefresh = true)
            result = queueApi.join(eventId)
        }
        val joined = (result as? ApiResult.Success)?.data ?: throw result.toException()
        if (joined.state != QueueWire.CLOSED && joined.queueToken.isNotBlank()) {
            tokens.setQueueToken(eventId, joined.queueToken)
            cache.addJoinedEvent(eventId)
        }
        return joined
    }

    suspend fun frontier(eventId: String): FrontierDto {
        val result = queueApi.frontier(eventId)
        return (result as? ApiResult.Success)?.data ?: throw result.toException()
    }

    /**
     * `GET /me`. With [lenient] (the caller only wants a display hint) any failure but a 410 reads as
     * [MeResult.Unavailable]; otherwise it throws.
     */
    suspend fun me(
        eventId: String,
        lenient: Boolean,
    ): MeResult {
        val token = tokens.getQueueToken(eventId) ?: return MeResult.Unavailable
        return when (val result = queueApi.me(eventId, token)) {
            is ApiResult.Success -> MeResult.Found(result.data)
            is ApiResult.Error ->
                when {
                    result.code == HTTP_GONE && result.errorCode() == ErrorCode.QUEUE_CLOSED -> MeResult.Closed
                    result.code == HTTP_GONE -> MeResult.Missed
                    result.code == HTTP_NOT_FOUND -> MeResult.NotFound
                    lenient -> MeResult.Unavailable
                    else -> throw result.toException()
                }
            is ApiResult.Failure -> if (lenient) MeResult.Unavailable else throw result.toException()
        }
    }

    /**
     * One claim attempt. A 401 throws [SignInRequiredException] (a guest cannot claim); everything unexpected throws
     * [DomainException].
     */
    suspend fun claim(eventId: String): ClaimStep {
        val token = tokens.getQueueToken(eventId) ?: throw DomainException(DomainResult.TechnicalError)
        return when (val result = queueApi.claim(eventId, token)) {
            is ApiResult.Success -> {
                tokens.setAdmissionToken(result.data.admissionToken)
                // Non-secret facts for the Detail screen; the Waiting Room keeps its own 60 s turn countdown.
                cache.setAdmission(
                    AdmissionInfo(
                        eventId = eventId,
                        slotId = result.data.slotId,
                        maxTickets = result.data.maxTickets,
                        expiresAtEpochMillis = result.data.expiresAt?.let(::parseStringDateMillis),
                    ),
                )
                ClaimStep.Admitted(result.data)
            }
            is ApiResult.Error -> claimError(result)
            is ApiResult.Failure -> throw result.toException()
        }
    }

    private fun claimError(result: ApiResult.Error): ClaimStep {
        val code = result.errorCode()
        return when {
            // Only an account-session problem asks the user to sign in; any other 401 is the device token.
            result.code == HTTP_UNAUTHORIZED && code == ErrorCode.UNAUTHORIZED -> throw SignInRequiredException()
            result.code == HTTP_UNAUTHORIZED -> ClaimStep.DeviceRejected(result.toException())
            result.code == HTTP_CONFLICT && code == ErrorCode.NO_STOCK -> ClaimStep.BackToWaiting
            result.code == HTTP_CONFLICT && (code == ErrorCode.NOT_YET || code == ErrorCode.FULL) ->
                ClaimStep.Retry(code, result.retryAfterSeconds)
            result.code == HTTP_GONE && code == ErrorCode.QUEUE_CLOSED -> ClaimStep.Closed
            result.code == HTTP_GONE -> ClaimStep.Missed
            else -> throw result.toException()
        }
    }

    /** `DELETE /me` (idempotent); the caller forgets the place on success. */
    suspend fun leave(
        eventId: String,
        queueToken: String,
    ): DomainResult<Unit> = queueApi.leave(eventId, queueToken).toDomainResult()

    /** The event for the poster copy; null when `GET /events` fails or does not list it (best effort). */
    suspend fun event(eventId: String): EventDto? =
        (eventApi.getEvents() as? ApiResult.Success)?.data?.items?.firstOrNull { it.eventId == eventId }

    /** Drops the admission token and its details when they belong to [eventId] (the user left that queue). */
    suspend fun forgetAdmissionOf(eventId: String) {
        if (cache.getAdmission()?.eventId == eventId) {
            tokens.clearAdmissionToken()
            cache.clearAdmission()
        }
    }

    /** Drops the stored queue token and the remembered event id. */
    suspend fun forget(eventId: String) {
        tokens.clearQueueToken(eventId)
        cache.removeJoinedEvent(eventId)
    }

    /** Registers the device again (after a rejected device token). Concurrent callers register once. */
    suspend fun reRegisterDevice() = ensureDevice(forceRefresh = true)

    private suspend fun ensureDevice(forceRefresh: Boolean) {
        val registered = devices.ensureRegistered(forceRefresh)
        if (registered !is DomainResult.Success) throw DomainException(registered)
    }

    private fun ApiResult.Error.errorCode(): ErrorCode = problem?.code ?: ErrorCode.UNKNOWN

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_CONFLICT = 409
        const val HTTP_GONE = 410
        const val HTTP_NOT_FOUND = 404
    }
}
