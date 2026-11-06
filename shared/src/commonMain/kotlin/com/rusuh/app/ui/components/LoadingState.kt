package com.rusuh.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rusuh.app.ui.theme.RusuhSpacing

/** Height of a single skeleton text line. */
private val SKELETON_LINE_HEIGHT = 16.dp

/** Fraction of the card width a skeleton line occupies (short vs full). */
private const val SKELETON_LINE_SHORT = 0.6f
private const val SKELETON_LINE_LONG = 0.9f

/** Shimmer animation sweep duration, milliseconds. */
private const val SHIMMER_DURATION_MS = 1_100

/** Default number of skeleton cards shown while loading. */
private const val DEFAULT_SKELETON_COUNT = 3

/** Poster skeleton aspect ratio; mirrors EventCard's 16:9 poster. */
private const val POSTER_SKELETON_RATIO = 16f / 9f

/** Horizontal extent the shimmer highlight sweeps across, in px. */
private const val SHIMMER_SWEEP = 900f

/** Width of the shimmer highlight band, in px. */
private const val SHIMMER_GRADIENT_WIDTH = 300f

/**
 * Loading placeholder for the home list: a column of shimmering skeleton cards
 * that mirror [EventCard]'s shape (poster + two text lines) so the layout does
 * not jump when real data arrives.
 *
 * The whole column carries a single content description so screen readers
 * announce "loading" once rather than reading each decorative skeleton block.
 *
 * @param count how many skeleton cards to draw.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    count: Int = DEFAULT_SKELETON_COUNT,
) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier.semantics { contentDescription = "Memuat daftar event" },
    ) {
        repeat(count) {
            SkeletonCard(brush = brush)
            Spacer(Modifier.height(RusuhSpacing.xl))
        }
    }
}

@Composable
private fun SkeletonCard(brush: Brush) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        // Poster placeholder: same 16:9 ratio as EventCard's PosterSlot.
        Spacer(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(POSTER_SKELETON_RATIO)
                    .clip(MaterialTheme.shapes.large)
                    .background(brush),
        )
        Column(modifier = Modifier.padding(RusuhSpacing.xl)) {
            SkeletonLine(brush = brush, widthFraction = SKELETON_LINE_LONG)
            Spacer(Modifier.height(RusuhSpacing.md))
            SkeletonLine(brush = brush, widthFraction = SKELETON_LINE_SHORT)
        }
    }
}

@Composable
private fun SkeletonLine(
    brush: Brush,
    widthFraction: Float,
) {
    Spacer(
        modifier =
            Modifier
                .fillMaxWidth(widthFraction)
                .height(SKELETON_LINE_HEIGHT)
                .clip(MaterialTheme.shapes.small)
                .background(brush),
    )
}

/**
 * An animated horizontal gradient that sweeps across the skeleton blocks.
 * Built from theme surface tokens so it stays readable in any scheme.
 */
@Composable
private fun rememberShimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = SHIMMER_DURATION_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "shimmer-progress",
    )
    val start = (progress * SHIMMER_SWEEP) - SHIMMER_GRADIENT_WIDTH
    return Brush.horizontalGradient(
        colors = listOf(base, highlight, base),
        startX = start,
        endX = start + SHIMMER_GRADIENT_WIDTH,
    )
}
