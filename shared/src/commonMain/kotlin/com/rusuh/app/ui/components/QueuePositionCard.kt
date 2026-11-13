package com.rusuh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.RusuhTextStyles
import com.rusuh.app.ui.theme.rusuhDisplayFontFamily
import com.rusuh.app.util.groupThousands
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.queue_position_eta_minutes
import rusuh_app.shared.generated.resources.queue_position_paused_description
import rusuh_app.shared.generated.resources.queue_position_rate
import rusuh_app.shared.generated.resources.queue_position_track_joined
import rusuh_app.shared.generated.resources.queue_position_track_serving
import rusuh_app.shared.generated.resources.queue_position_track_turn
import rusuh_app.shared.generated.resources.queue_position_updated
import rusuh_app.shared.generated.resources.queue_position_waiting

/**
 * Phase of the queue position card. Kept rendering-free so the phase -> look
 * mapping can be unit-tested in commonTest without a Compose harness.
 */
enum class QueuePhase {
    /** Queue is admitting: pink number, ETA, rate and progress track. */
    Waiting,

    /** Queue is paused (stock or operator): number frozen in ink, no ETA. */
    Paused,
}

/**
 * Where the customer sits on the queue line. Numbers are the raw queue sequence
 * (API: `Frontier.served`, `QueuePlace.seq`); the position shown to the user is
 * `myNumber - nowServing`.
 *
 * @param servedAtJoin the "now serving" value at the moment the customer joined.
 * @param nowServing the number currently being served.
 * @param myNumber the customer's own queue number (`seq`).
 */
@Immutable
data class QueueProgressUi(
    val servedAtJoin: Int,
    val nowServing: Int,
    val myNumber: Int,
) {
    init {
        require(servedAtJoin <= myNumber) { "servedAtJoin ($servedAtJoin) cannot exceed myNumber ($myNumber)" }
        require(nowServing >= servedAtJoin) { "nowServing ($nowServing) cannot precede servedAtJoin ($servedAtJoin)" }
    }

    /** Customer position counted from the front: `seq - served`. */
    val position: Int get() = (myNumber - nowServing).coerceAtLeast(1)

    /** Share of the track already served, in 0..1. */
    val fraction: Float
        get() {
            val span = myNumber - servedAtJoin
            return if (span <= 0) 0f else ((nowServing - servedAtJoin).toFloat() / span).coerceIn(0f, 1f)
        }
}

/**
 * Display data for the queue-position component.
 *
 * @param position 1-based position from the front ("Anda antrian ke-[position]"). Must be >= 1.
 * @param phase waiting or paused.
 * @param estimatedWaitMinutes ETA in whole minutes ("± n menit lagi"); null hides the slot.
 * @param ratePerMinute throughput ("x orang masuk / menit"); null hides it.
 * @param updatedSecondsAgo freshness ("Diperbarui n detik lalu"); null hides it.
 * @param progress progress track data; null hides the track.
 */
@Immutable
data class QueuePositionUi(
    val position: Int,
    val phase: QueuePhase = QueuePhase.Waiting,
    val estimatedWaitMinutes: Int? = null,
    val ratePerMinute: Int? = null,
    val updatedSecondsAgo: Int? = null,
    val progress: QueueProgressUi? = null,
) {
    init {
        require(position >= 1) { "position must be >= 1, was $position" }
        require(estimatedWaitMinutes == null || estimatedWaitMinutes >= 0) { "estimatedWaitMinutes must be >= 0" }
    }
}

/**
 * Queue-position component for the Waiting Room: lead line, oversized position
 * number, ETA headline with freshness/rate meta, and the progress track.
 *
 * Stateless: takes [data] only. The whole block is one semantic unit so screen
 * readers announce position + ETA in a single pass.
 */
@Composable
fun QueuePositionCard(
    data: QueuePositionUi,
    modifier: Modifier = Modifier,
) {
    val paused = data.phase == QueuePhase.Paused
    val description = buildQueueDescription(data)
    Column(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(
            text = stringResource(Res.string.queue_position_waiting),
            style = MaterialTheme.typography.bodyLarge,
            color = RusuhColors.Ink,
        )
        Spacer(Modifier.height(RusuhSpacing.md))
        QueueBigNumber(text = formatQueueNumber(data.position), paused = paused)
        if (!paused) {
            data.estimatedWaitMinutes?.let { minutes ->
                Spacer(Modifier.height(RusuhSpacing.xl))
                Text(
                    text = stringResource(Res.string.queue_position_eta_minutes, minutes),
                    style = RusuhTextStyles.etaHeadline.copy(fontFamily = rusuhDisplayFontFamily()),
                    color = RusuhColors.Ink,
                )
            }
            QueueMetaLine(data)
            data.progress?.let { progress ->
                Spacer(Modifier.height(RusuhSpacing.xxl))
                QueueProgressTrack(progress = progress)
            }
        }
    }
}

/**
 * Oversized display number with the offset "misprint" plate behind it. Used for
 * the queue position and for the pre-queue countdown.
 *
 * @param paused ink number on a muted plate instead of pink on blue.
 * @param ink forces the ink (black) face, e.g. for the countdown.
 */
@Composable
fun QueueBigNumber(
    text: String,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    ink: Boolean = false,
) {
    val style = queueNumberStyle(text).copy(fontFamily = rusuhDisplayFontFamily())
    val face = if (ink || paused) RusuhColors.Ink else RusuhColors.Pink
    val plate = if (paused) RusuhColors.PaperDeep else RusuhColors.Blue
    Box(modifier = modifier.padding(end = RusuhSpacing.sm)) {
        Text(
            text = text,
            style = style,
            color = plate,
            maxLines = 1,
            softWrap = false,
            // Decorative shadow copy: hidden from screen readers so the number is read once.
            modifier = Modifier.offset(x = RusuhSpacing.xs, y = RusuhSpacing.xxs).clearAndSetSemantics {},
        )
        Text(text = text, style = style, color = face, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun QueueMetaLine(data: QueuePositionUi) {
    val parts = mutableListOf<String>()
    data.updatedSecondsAgo?.let { parts += stringResource(Res.string.queue_position_updated, it) }
    data.ratePerMinute?.let { parts += stringResource(Res.string.queue_position_rate, it) }
    if (parts.isEmpty()) return
    Spacer(Modifier.height(RusuhSpacing.xs))
    Text(
        text = parts.joinToString(separator = META_SEPARATOR),
        style = MaterialTheme.typography.bodySmall,
        color = RusuhColors.InkMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Progress track: bordered bar, blue fill up to the served share, pink marker
 * for "my number", with join/turn labels and the "now serving" line underneath.
 */
@Composable
private fun QueueProgressTrack(progress: QueueProgressUi) {
    val fraction = progress.fraction
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(RusuhSpacing.queueTrackHeight)
                .border(RusuhSpacing.borderBold, RusuhColors.Ink),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(RusuhColors.Blue).testTag(PROGRESS_FILL_TAG))
        Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.End) {
            Box(Modifier.width(RusuhSpacing.queueTrackMarker).fillMaxHeight().background(RusuhColors.Pink))
        }
    }
    Spacer(Modifier.height(RusuhSpacing.md))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = stringResource(Res.string.queue_position_track_joined),
            style = MaterialTheme.typography.bodySmall,
            color = RusuhColors.Ink,
        )
        Text(
            text = stringResource(Res.string.queue_position_track_turn),
            style = MaterialTheme.typography.bodySmall,
            color = RusuhColors.Ink,
            textAlign = TextAlign.End,
        )
    }
    Spacer(Modifier.height(RusuhSpacing.xs))
    Text(
        text =
            stringResource(
                Res.string.queue_position_track_serving,
                formatQueueNumber(progress.nowServing),
                formatQueueNumber(progress.myNumber),
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = RusuhColors.Ink,
    )
}

/** Indonesian thousands grouping with a dot: 1248 -> "1.248". Pure function. */
fun formatQueueNumber(value: Int): String {
    return value.toLong().groupThousands()
}

/** Formats a countdown in whole seconds as m:ss ("8:42", "0:52"). Pure function. */
fun formatCountdown(totalSeconds: Int): String {
    val clamped = totalSeconds.coerceAtLeast(0)
    val seconds = (clamped % SECONDS_PER_MINUTE).toString().padStart(2, '0')
    return "${clamped / SECONDS_PER_MINUTE}:$seconds"
}

/** Picks the regular or compact number style so long numbers still fit a phone width. Pure function. */
fun queueNumberStyle(text: String) =
    if (text.length <= MAX_FULL_SIZE_CHARS) RusuhTextStyles.queueNumber else RusuhTextStyles.queueNumberCompact

/** Accent (face) color for a phase. Pure function. */
fun QueuePhase.faceColor(): Color =
    when (this) {
        QueuePhase.Waiting -> RusuhColors.Pink
        QueuePhase.Paused -> RusuhColors.Ink
    }

/** Accessibility description combining position and ETA; resolved in composition. */
@Composable
internal fun buildQueueDescription(data: QueuePositionUi): String =
    buildString {
        val lead = stringResource(Res.string.queue_position_waiting).trimEnd()
        // "Anda antrian ke-" glues to the number; "You are number" needs a space.
        val separator = if (lead.endsWith("-")) "" else " "
        append("$lead$separator${formatQueueNumber(data.position)}.")
        if (data.phase == QueuePhase.Paused) {
            append(" ${stringResource(Res.string.queue_position_paused_description)}")
        } else {
            data.estimatedWaitMinutes?.let { append(" ${stringResource(Res.string.queue_position_eta_minutes, it)}.") }
            data.ratePerMinute?.let { append(" ${stringResource(Res.string.queue_position_rate, it)}.") }
        }
    }

private const val META_SEPARATOR = " · "
private const val SECONDS_PER_MINUTE = 60
private const val MAX_FULL_SIZE_CHARS = 5
internal const val PROGRESS_FILL_TAG = "queue-progress-blue-fill"
