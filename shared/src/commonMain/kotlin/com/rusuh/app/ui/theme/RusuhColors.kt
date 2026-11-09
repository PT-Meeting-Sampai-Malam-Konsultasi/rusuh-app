package com.rusuh.app.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Raw color tokens for the Rusuh design system. */
object RusuhColors {
    val Primary = Color(0xFFD32F2F)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFFFDAD6)
    val OnPrimaryContainer = Color(0xFF410002)

    val Secondary = Color(0xFF1E88E5)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFD3E4FF)
    val OnSecondaryContainer = Color(0xFF001C38)

    val Background = Color(0xFFFFFBFF)
    val OnBackground = Color(0xFF201A19)

    val Surface = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFF201A19)
    val SurfaceVariant = Color(0xFFF5DDDA)
    val OnSurfaceVariant = Color(0xFF534341)

    val Error = Color(0xFFBA1A1A)
    val OnError = Color(0xFFFFFFFF)

    val Outline = Color(0xFF857370)

    // Semantic status tokens for event availability (StatusBadge / EventCard).
    // Material3's ColorScheme has no slots for these, so they live as named
    // tokens here and are surfaced through RusuhStatusColors (see below) rather
    // than being inlined as hex in components.
    val StatusAvailable = Color(0xFF2E7D32) // green: tickets on sale
    val OnStatusAvailable = Color(0xFFFFFFFF)
    val StatusLimited = Color(0xFFE8820E) // orange: temporarily full / "Penuh sementara"
    val OnStatusLimited = Color(0xFFFFFFFF)
    val StatusSoldOut = Color(0xFF6B6B6B) // grey: sold out / sales ended
    val OnStatusSoldOut = Color(0xFFFFFFFF)
    val StatusUpcoming = Color(0xFF1E88E5) // blue: coming soon / "Segera dijual"
    val OnStatusUpcoming = Color(0xFFFFFFFF)

    // Hi-fi / Figma "paper & ink" palette used by the Home event list (additive:
    // the Material scheme above is untouched so other screens keep their look).
    val Paper = Color(0xFFF2EEE3)
    val Paper2 = Color(0xFFE8E2D2)
    val PaperCard = Color(0xFFFBF9F3)
    val Ink = Color(0xFF141414)
    val Ink2 = Color(0xFF4A4740)
    val Ink3 = Color(0xFF6B665E)
    val Hair = Color(0xFFCFC8B6)
    val Pink = Color(0xFFD81F72)
    val PinkInk = Color(0xFFB8125C)
    val PinkTint = Color(0xFFF8D3E2)
    val Blue = Color(0xFF2F5BEA)
    val BlueInk = Color(0xFF1F3FB8)
    val Green = Color(0xFF16784A)

    // Aliases used by the Waiting Room code (same values as Paper2 / Ink2 / Ink3).
    val PaperDeep = Paper2
    val InkMuted = Ink2
    val InkSubtle = Ink3

    // Scrim applied over a poster image so overlaid chrome stays legible.
    val PosterScrim = Color(0x99000000)
}

/**
 * Semantic status color pairs (container + content) for event availability.
 *
 * These complement [RusuhLightColorScheme]; Material3's [androidx.compose.material3.ColorScheme]
 * has no dedicated slots for domain status colors, so components read them from here.
 */
data class StatusColorPair(
    val container: Color,
    val content: Color,
    val border: Color = container,
    val dashedBorder: Boolean = false,
)

object RusuhStatusColors {
    // Original status pairs, still used by queue rows and other screens.
    val available = StatusColorPair(RusuhColors.StatusAvailable, RusuhColors.OnStatusAvailable)
    val limited = StatusColorPair(RusuhColors.StatusLimited, RusuhColors.OnStatusLimited)
    val soldOut = StatusColorPair(RusuhColors.StatusSoldOut, RusuhColors.OnStatusSoldOut)
    val upcoming = StatusColorPair(RusuhColors.StatusUpcoming, RusuhColors.OnStatusUpcoming)

    // Figma event-card stamps (paper & ink style).

    /** Queue / waiting room open: pink stamp, white text. */
    val waitingRoomOpen = StatusColorPair(RusuhColors.Pink, RusuhColors.OnPrimary)

    /** On sale: green stamp. */
    val onSale = StatusColorPair(RusuhColors.Green, RusuhColors.OnStatusAvailable)

    /** Almost gone: orange stamp. */
    val almostGone = StatusColorPair(RusuhColors.StatusLimited, RusuhColors.OnStatusLimited)

    /** Temporarily full: paper card with a dashed ink outline. */
    val temporarilyFull =
        StatusColorPair(RusuhColors.PaperCard, RusuhColors.Ink, RusuhColors.Ink, dashedBorder = true)

    /** Sold out: solid ink stamp, paper text. */
    val soldOutStamp = StatusColorPair(RusuhColors.Ink, RusuhColors.Paper)

    /** Coming soon: paper card, blue outline and text. */
    val upcomingStamp = StatusColorPair(RusuhColors.PaperCard, RusuhColors.BlueInk, RusuhColors.BlueInk)

    /** Sale ended: muted paper stamp. */
    val ended = StatusColorPair(RusuhColors.Paper2, RusuhColors.Ink2, RusuhColors.Ink2)
}

internal val RusuhLightColorScheme =
    lightColorScheme(
        primary = RusuhColors.Primary,
        onPrimary = RusuhColors.OnPrimary,
        primaryContainer = RusuhColors.PrimaryContainer,
        onPrimaryContainer = RusuhColors.OnPrimaryContainer,
        secondary = RusuhColors.Secondary,
        onSecondary = RusuhColors.OnSecondary,
        secondaryContainer = RusuhColors.SecondaryContainer,
        onSecondaryContainer = RusuhColors.OnSecondaryContainer,
        background = RusuhColors.Background,
        onBackground = RusuhColors.OnBackground,
        surface = RusuhColors.Surface,
        onSurface = RusuhColors.OnSurface,
        surfaceVariant = RusuhColors.SurfaceVariant,
        onSurfaceVariant = RusuhColors.OnSurfaceVariant,
        error = RusuhColors.Error,
        onError = RusuhColors.OnError,
        outline = RusuhColors.Outline,
    )
