package com.rusuh.app.data.remote.mapper

import com.rusuh.app.data.remote.dto.FrontierDto
import com.rusuh.app.feature.waitingroom.domain.model.QueuePauseReason

private const val MILLIS_PER_SECOND = 1_000L

/** Why a paused queue is paused: `stock` is the full-stock pause, anything else counts as the operator checking. */
internal fun FrontierDto.pauseReason(): QueuePauseReason =
    if (reason == QueueWire.REASON_STOCK) QueuePauseReason.StockFull else QueuePauseReason.OperatorCheck

/** The frontier's sale opening (Unix seconds on the wire) as epoch millis. */
internal fun FrontierDto.opensAtMillis(): Long? = opensAt?.times(MILLIS_PER_SECOND)

/** The frontier's server time (Unix seconds on the wire) as epoch millis. */
internal fun FrontierDto.tsMillis(): Long? = ts?.times(MILLIS_PER_SECOND)
