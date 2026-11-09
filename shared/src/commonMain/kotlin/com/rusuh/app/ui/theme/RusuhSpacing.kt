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

    /** Tonal elevation of the shared bottom navigation bar. */
    val bottomBarElevation = 3.dp

    /** Side length of the square poster thumbnail on a My Queues row. */
    val queueThumb = 64.dp

    /** Height of a placeholder skeleton row on the My Queues loading state. */
    val queueSkeletonRow = 96.dp

    /** Side length of a square +/- stepper button on the Detail Ticket page. */
    val stepperButton = 36.dp

    /** Border width of a stepper button. */
    val stepperBorder = 1.dp

    /** Fixed width of the quantity readout between the stepper buttons. */
    val stepperValue = 28.dp

    // Hi-fi mobile list values (analysis/hifi/assets/{rusuh,home}.css, max-width 720px).

    /** Stroke width of ink outlines (`--bw`). */
    val inkBorder = 2.5.dp

    /** Stroke width of a status stamp outline. */
    val stampBorder = 2.dp

    /** X/Y offset of the zero-blur hard shadow under a poster thumbnail. */
    val hardShadowOffset = 3.dp

    /** Width of the poster thumbnail on an event row (4:5 aspect). */
    val posterThumbWidth = 104.dp

    /** Poster thumbnail aspect ratio, width / height. */
    const val POSTER_ASPECT = 4f / 5f

    /** Vertical padding of an event row. */
    val eventRowPadding = 18.dp

    /** Width of the hairline between event rows. */
    val hairline = 1.dp

    /** Rotation (degrees) of the status stamp and poster subtitle chip. */
    const val STAMP_ROTATION = -2f

    /** Alternating tilt (degrees) of poster thumbnails: odd rows, even rows. */
    const val POSTER_TILT_ODD = -0.8f
    const val POSTER_TILT_EVEN = 0.9f

    /** Alpha applied to a disabled (sold-out) card. */
    const val DISABLED_ALPHA = 0.5f
}
