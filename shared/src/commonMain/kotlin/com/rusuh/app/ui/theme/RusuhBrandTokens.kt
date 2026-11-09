package com.rusuh.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Brand-surface tokens from the Figma / hi-fi design language: warm paper
 * background, hard ink outlines and a pink accent. Additive to
 * [RusuhColors] (which keeps the Material baseline) so existing screens are
 * untouched; Figma-aligned screens read these directly.
 */
object RusuhBrandColors {
    val Paper = Color(0xFFF2EEE3)
    val PaperDeep = Color(0xFFE8E2D2)
    val Card = Color(0xFFFBF9F3)
    val Ink = Color(0xFF141414)
    val Ink2 = Color(0xFF4A4740)
    val Ink3 = Color(0xFF6B665E)
    val Hair = Color(0xFFCFC8B6)
    val Pink = Color(0xFFD81F72)
    val PinkInk = Color(0xFFB8125C)
    val PinkTint = Color(0xFFF8D3E2)
}

/** Neo-brutalist outline / hard-shadow metrics shared by Figma-aligned surfaces. */
object RusuhBrandMetrics {
    /** Ink outline width of cards, bars and badges. */
    val borderWidth = 2.5.dp

    /** Offset of the hard (blur-less) ink shadow behind a card. */
    val shadowOffset = 4.dp

    /** Height of the top app bar (logo + language toggle). */
    val appBarHeight = 60.dp
}
