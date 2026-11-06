package com.rusuh.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing tokens for the Rusuh design system.
 *
 * Material3 has no theme slot for spacing, so components read these named
 * tokens directly rather than inlining raw dp values. Keep the scale small and
 * consistent (4dp base step).
 */
object RusuhSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 6.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
    val xxl = 24.dp

    /** Diameter of the status indicator dot on a StatusBadge. */
    val statusDot = 8.dp

    /** Outer bounds of the Waiting Room queue indicator (pulsing halo). */
    val queueIndicator = 48.dp

    /** Diameter of the solid core dot inside the queue indicator. */
    val queueDotCore = 16.dp

    /** Alpha applied to a disabled (sold-out) card. */
    const val DISABLED_ALPHA = 0.5f
}
