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

    // Poster-sablon palette, taken from the hi-fi design CSS (analysis/hifi/assets/rusuh.css).
    val Paper = Color(0xFFF2EEE3) // page background
    val PaperAlt = Color(0xFFE8E2D2)
    val Card = Color(0xFFFBF9F3) // raised surface (stamps, buttons)
    val Ink = Color(0xFF141414) // text, outlines, hard shadows
    val Ink2 = Color(0xFF4A4740) // secondary text
    val Ink3 = Color(0xFF6B665E)
    val Hair = Color(0xFFCFC8B6) // 1dp row dividers
    val Pink = Color(0xFFD81F72) // "you" / live queue
    val PinkInk = Color(0xFFB8125C)
    val Blue = Color(0xFF2F5BEA) // "the event"
    val BlueInk = Color(0xFF1F3FB8)

    // Status stamp tokens (StatusBadge). Material3's ColorScheme has no slots for
    // these, so they live here and are surfaced through RusuhStatusColors below.
    val StatusAvailable = Pink // .stamp.on
    val OnStatusAvailable = Color(0xFFFFFFFF)
    val StatusLimited = Card // .stamp.full (dashed ink outline)
    val OnStatusLimited = Ink
    val StatusSoldOut = Ink // .stamp.sold
    val OnStatusSoldOut = Paper
    val StatusUpcoming = Card // .stamp.soon
    val OnStatusUpcoming = BlueInk

    // Darkening applied over a poster image so overlaid chrome stays legible.
    val PosterScrim = Color(0x99000000)
}

/**
 * Semantic status color pairs (container + content) for event availability.
 *
 * These complement [RusuhLightColorScheme]; Material3's [androidx.compose.material3.ColorScheme]
 * has no dedicated slots for domain status colors, so components read them from here.
 */
data class StatusColorPair(val container: Color, val content: Color)

object RusuhStatusColors {
    val available = StatusColorPair(RusuhColors.StatusAvailable, RusuhColors.OnStatusAvailable)
    val limited = StatusColorPair(RusuhColors.StatusLimited, RusuhColors.OnStatusLimited)
    val soldOut = StatusColorPair(RusuhColors.StatusSoldOut, RusuhColors.OnStatusSoldOut)
    val upcoming = StatusColorPair(RusuhColors.StatusUpcoming, RusuhColors.OnStatusUpcoming)
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
