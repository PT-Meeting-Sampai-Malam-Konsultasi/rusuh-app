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

    /** Width of the poster thumbnail on an event card row (poster ratio is 4:5). */
    val posterThumbWidth = 104.dp

    /** Offset of the hard "print" shadow behind posters. */
    val posterShadow = 3.dp

    /** Offset of the hard shadow behind action buttons. */
    val buttonShadow = 4.dp

    /** Border width of status stamps. */
    val stampBorder = 2.dp

    /** Heavy rule / button border width (the hi-fi's 2.5px). */
    val ruleThick = 2.5.dp

    /** Hairline divider width. */
    val hairline = 1.dp

    /** Height of the Home app bar. */
    val appBarHeight = 60.dp

    /** Minimum touch height of an event card action button. */
    val actionMinHeight = 44.dp

    /** Alpha applied to a disabled (sold-out) card. */
    const val DISABLED_ALPHA = 0.5f
}
