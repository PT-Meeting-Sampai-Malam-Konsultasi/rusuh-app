package com.rusuh.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Text style tokens. */
object RusuhTextStyles {
    val h1 = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
    val h2 = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
    val body1 = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val body2 = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val logo = TextStyle(fontSize = 28.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.28).sp)
    val sectionTitle = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
    val eventTitle = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
    val stamp = TextStyle(fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.55.sp)
    val button = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)

    // Waiting Room display styles. Font family is applied by the caller (display font).
    val queueNumber =
        TextStyle(fontSize = 120.sp, lineHeight = 112.sp, fontWeight = FontWeight.Bold, letterSpacing = (-4).sp)
    val queueNumberCompact = queueNumber.copy(fontSize = 92.sp, lineHeight = 88.sp, letterSpacing = (-3).sp)
    val displayHeadline =
        TextStyle(fontSize = 64.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
    val etaHeadline = TextStyle(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
    val queueSectionTitle = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
}

internal val RusuhTypography =
    Typography(
        headlineLarge = RusuhTextStyles.h1,
        headlineMedium = RusuhTextStyles.h2,
        bodyLarge = RusuhTextStyles.body1,
        bodyMedium = RusuhTextStyles.body2,
        bodySmall = RusuhTextStyles.caption,
        labelLarge = RusuhTextStyles.button,
        displaySmall = RusuhTextStyles.logo,
        headlineSmall = RusuhTextStyles.sectionTitle,
        titleSmall = RusuhTextStyles.eventTitle,
        labelSmall = RusuhTextStyles.stamp,
    )

/**
 * Builds the Rusuh [Typography] with the brand font families applied, preserving every existing
 * size/weight/lineHeight token. Display styles (headlineLarge/headlineMedium) use Bricolage
 * Grotesque; body and label styles use Space Grotesk.
 *
 * Composable because the resource fonts load within a composition; call it inside the theme.
 */
@Composable
internal fun rusuhTypography(): Typography {
    val display: FontFamily = rusuhDisplayFontFamily()
    val body: FontFamily = rusuhBodyFontFamily()
    return RusuhTypography.copy(
        headlineLarge = RusuhTypography.headlineLarge.copy(fontFamily = display),
        headlineMedium = RusuhTypography.headlineMedium.copy(fontFamily = display),
        bodyLarge = RusuhTypography.bodyLarge.copy(fontFamily = body),
        bodyMedium = RusuhTypography.bodyMedium.copy(fontFamily = body),
        bodySmall = RusuhTypography.bodySmall.copy(fontFamily = body),
        labelLarge = RusuhTypography.labelLarge.copy(fontFamily = body),
        displaySmall = RusuhTypography.displaySmall.copy(fontFamily = display),
        headlineSmall = RusuhTypography.headlineSmall.copy(fontFamily = display),
        titleSmall = RusuhTypography.titleSmall.copy(fontFamily = display),
        labelSmall = RusuhTypography.labelSmall.copy(fontFamily = body),
    )
}
