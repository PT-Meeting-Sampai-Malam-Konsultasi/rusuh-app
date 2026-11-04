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
}

internal val RusuhLightColorScheme = lightColorScheme(
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
