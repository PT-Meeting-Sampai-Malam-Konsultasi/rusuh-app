package com.rusuh.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.RusuhStatusColors
import com.rusuh.app.ui.theme.StatusColorPair

/**
 * Phase of a customer's wait in the queue. Drives the card's accent color and
 * copy. Kept rendering-free so the phase->label/color mapping can be
 * unit-tested in commonTest without a Compose harness.
 *
 * Mirrors FR-04 spec: a customer waits (FR-06/07), is let through when their
 * turn arrives (FR-08), or the queue closes when tickets sell out (FR-08/T-03).
 */
enum class QueuePhase {
    /** Still in line; position counts down as those ahead are let through. */
    Waiting,

    /** The customer's turn has arrived — about to be moved to the ticket page. */
    YourTurn,

    /** Queue closed because tickets sold out; the customer will not get in. */
    QueueClosed,
}

/**
 * Display data for the queue-position card. UI-facing view model: every string
 * is already localized/formatted by the caller so the component stays clock-
 * and locale-agnostic.
 *
 * @param position the customer's 1-based position counted from the front
 *   ("Anda antrian ke-[position]"). Must be >= 1.
 * @param peopleAhead how many are still ahead (typically position - 1); kept
 *   explicit so the caller controls the exact copy. Must be in 0..position-1.
 * @param phase current wait phase, drives accent color and messaging.
 * @param estimatedWait pre-formatted estimated wait (e.g. "~8 menit"); null
 *   hides the ETA slot. Per T-03 the ETA is mandatory in production, but the
 *   slot stays optional so the stateless component can render before an
 *   estimate exists (loading) without a sentinel value.
 */
data class QueuePositionUi(
    val position: Int,
    val peopleAhead: Int,
    val phase: QueuePhase,
    val estimatedWait: String? = null,
) {
    init {
        require(position >= 1) { "position must be >= 1, was $position" }
        require(peopleAhead >= 0) { "peopleAhead must be >= 0, was $peopleAhead" }
        // You cannot have more people ahead than your own position implies
        // (position counts from the front, so peopleAhead is at most position - 1).
        // Kept as a separate field rather than derived so the caller controls the
        // exact copy, but the two must stay consistent.
        require(peopleAhead <= position - 1) {
            "peopleAhead ($peopleAhead) cannot exceed position - 1 (${position - 1})"
        }
    }
}

/**
 * Queue-position card for the Waiting Room.
 *
 * Stateless and token-driven: takes a [QueuePositionUi] and renders the big
 * position number, the people-ahead count, an optional ETA slot and a
 * phase-aware visual indicator (a pulsing dot while waiting, a steady dot
 * otherwise). The whole card is one semantic unit so screen readers announce
 * position + phase + wait in a single pass.
 *
 * NO ViewModel, NO data fetching — the caller supplies [data].
 */
@Composable
fun QueuePositionCard(
    data: QueuePositionUi,
    modifier: Modifier = Modifier,
) {
    val colors = data.phase.accentColors()
    val description = buildQueueDescription(data)

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = description },
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(RusuhSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QueueIndicator(phase = data.phase, color = colors.container)
            Spacer(Modifier.height(RusuhSpacing.xl))
            Text(
                text = data.phase.positionLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(RusuhSpacing.xs))
            Text(
                text = data.position.toString(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colors.container,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(RusuhSpacing.md))
            Text(
                text = peopleAheadLabel(data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            data.estimatedWait?.let { eta ->
                Spacer(Modifier.height(RusuhSpacing.xs))
                Text(
                    text = "Estimasi tunggu $eta",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Phase-aware visual indicator. A pulsing dot while [QueuePhase.Waiting]
 * conveys "live / still moving"; other phases show a steady dot so the motion
 * only runs when something is actually changing.
 */
@Composable
private fun QueueIndicator(
    phase: QueuePhase,
    color: androidx.compose.ui.graphics.Color,
) {
    val scale =
        if (phase == QueuePhase.Waiting) {
            val transition = rememberInfiniteTransition(label = "queuePulse")
            transition.animateFloat(
                initialValue = PULSE_MIN_SCALE,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = PULSE_DURATION_MS, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "queuePulseScale",
            ).value
        } else {
            1f
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(RusuhSpacing.queueIndicator),
    ) {
        // Soft halo behind the core dot, scaled by the pulse while waiting.
        Box(
            modifier =
                Modifier
                    .size(RusuhSpacing.queueIndicator)
                    .scale(scale)
                    .alpha(HALO_ALPHA)
                    .background(color = color, shape = CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .size(RusuhSpacing.queueDotCore)
                    .background(color = color, shape = CircleShape),
        )
    }
}

/** Accent color pair for a phase. Pure function — unit-tested directly. */
fun QueuePhase.accentColors(): StatusColorPair =
    when (this) {
        QueuePhase.Waiting -> RusuhStatusColors.upcoming
        QueuePhase.YourTurn -> RusuhStatusColors.available
        QueuePhase.QueueClosed -> RusuhStatusColors.soldOut
    }

/** Headline above the position number for a phase. Pure function — unit-tested directly. */
fun QueuePhase.positionLabel(): String =
    when (this) {
        QueuePhase.Waiting -> "Anda antrian ke-"
        QueuePhase.YourTurn -> "Giliran Anda — antrian ke-"
        QueuePhase.QueueClosed -> "Antrian ditutup — antrian ke-"
    }

/** People-ahead line for the card. Pure function — unit-tested directly. */
internal fun peopleAheadLabel(data: QueuePositionUi): String =
    when (data.phase) {
        QueuePhase.YourTurn -> "Tidak ada antrian di depan Anda"
        QueuePhase.QueueClosed -> "Tiket telah habis terjual"
        QueuePhase.Waiting ->
            when (data.peopleAhead) {
                0 -> "Tidak ada antrian di depan Anda"
                else -> "${data.peopleAhead} orang di depan Anda"
            }
    }

/** Full accessibility description combining position, phase and wait. Pure function. */
internal fun buildQueueDescription(data: QueuePositionUi): String =
    buildString {
        append("${data.phase.positionLabel()}${data.position}. ")
        append(peopleAheadLabel(data))
        append(".")
        data.estimatedWait?.let { append(" Estimasi tunggu $it.") }
    }

private const val PULSE_MIN_SCALE = 0.6f
private const val PULSE_DURATION_MS = 900
private const val HALO_ALPHA = 0.25f
