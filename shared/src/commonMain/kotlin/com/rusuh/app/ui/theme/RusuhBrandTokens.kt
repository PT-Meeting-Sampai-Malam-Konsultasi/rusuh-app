package com.rusuh.app.ui.theme

/**
 * Brand-surface aliases kept for the Figma-aligned screens that read
 * `RusuhBrandColors` / `RusuhBrandMetrics`. They hold no values of their own:
 * every entry points at [RusuhColors] / [RusuhSpacing], the single source of
 * truth, so the hex values and dp cannot drift apart.
 */
object RusuhBrandColors {
    val Paper = RusuhColors.Paper
    val PaperDeep = RusuhColors.Paper2
    val Card = RusuhColors.PaperCard
    val Ink = RusuhColors.Ink
    val Ink2 = RusuhColors.Ink2
    val Ink3 = RusuhColors.Ink3
    val Hair = RusuhColors.Hair
    val Pink = RusuhColors.Pink
    val PinkInk = RusuhColors.PinkInk
    val PinkTint = RusuhColors.PinkTint
}

/** Neo-brutalist outline / hard-shadow metrics, aliased to [RusuhSpacing]. */
object RusuhBrandMetrics {
    /** Ink outline width of cards, bars and badges. */
    val borderWidth = RusuhSpacing.ruleThick

    /** Offset of the hard (blur-less) ink shadow behind a card. */
    val shadowOffset = RusuhSpacing.hardShadow

    /** Height of the top app bar (logo + language toggle). */
    val appBarHeight = RusuhSpacing.appBarHeight
}
