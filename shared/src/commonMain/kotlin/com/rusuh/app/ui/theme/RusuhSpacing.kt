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

    /** Heavy border width used by Waiting Room tracks, timer and stamp. */
    val borderBold = 2.dp

    /** Offset of the hard (blur-free) drop shadow behind posters and buttons. */
    val hardShadow = 4.dp

    /** Height of the Waiting Room queue progress track. */
    val queueTrackHeight = 36.dp

    /** Width of the "my number" marker on the queue progress track. */
    val queueTrackMarker = 6.dp

    /** Minimum touch height of a primary Waiting Room button. */
    val primaryButtonHeight = 56.dp

    val xxxl = 32.dp

    /** Hairline separator between list rows (Figma 1px). */
    val borderThin = 1.dp

    /** Standard ink border on boxes, inputs and buttons (Figma 2px). */
    val borderMedium = 2.dp

    /** Heavy rule under section headers, top/bottom bars and stepper dividers (Figma 2.5px). */
    val borderHeavy = 2.5.dp

    /** Hard (no blur) offset shadow on small chips and the poster. */
    val shadowSm = 3.dp

    /** Hard offset shadow on primary buttons and cards. */
    val shadowMd = 4.dp

    /** Height of the Detail Ticket top bar. */
    val topBarHeight = 60.dp

    /** Height of the sticky bottom bar content (excluding its top rule). */
    val bottomBarHeight = 80.dp

    /** Size of the quantity stepper (Figma 129x45) and its centre cell. */
    val stepperWidth = 129.dp
    val stepperHeight = 45.dp
    val stepperCell = 40.dp

    /** Minimum height of a self/other toggle cell. */
    val toggleHeight = 48.dp

    /** Height of a text input (Figma 52). */
    val inputHeight = 52.dp

    /** Height of one OTP digit box (width flexes so six boxes fill the row). */
    val otpBoxHeight = 56.dp

    /** Diameter of the red "!" disc before an error message. */
    val errorIcon = 18.dp

    /** Width of the hyphen between the two OTP box groups. */
    val otpSeparatorWidth = 8.dp

    /** Side length of the consent checkbox square (the row around it is 44dp+). */
    val checkboxSize = 24.dp

    /** Event poster thumbnail in the Detail header (Figma 58x72). */
    val posterWidth = 58.dp
    val posterHeight = 72.dp

    /** Numbered square on a section header (Figma 28). */
    val sectionNumber = 28.dp

    /** Height of the primary CTA button (Figma 54). */
    val ctaHeight = 54.dp

    /** Alpha applied to a disabled (sold-out) card. */
    const val DISABLED_ALPHA = 0.5f
}
