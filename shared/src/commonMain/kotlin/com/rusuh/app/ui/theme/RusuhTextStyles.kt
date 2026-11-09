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

/**
 * Brand text styles for the Figma-aligned Detail Ticket screen. Raw sizes/weights only;
 * apply the brand family with [display] (Bricolage Grotesque) or [body] (Space Grotesk).
 */
object RusuhBrandTextStyles {
    val hero =
        TextStyle(fontSize = 30.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
    val sectionTitle = TextStyle(fontSize = 26.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val itemTitle = TextStyle(fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
    val subtitle = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    val counter =
        TextStyle(fontSize = 22.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.22.sp)
    val stat = TextStyle(fontSize = 26.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)
    val number = TextStyle(fontSize = 18.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold)
    val stepperValue = TextStyle(fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold)
    val sheetHeadline =
        TextStyle(fontSize = 36.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
    val cta = TextStyle(fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)

    val chip = TextStyle(fontSize = 15.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold)

    val note = TextStyle(fontSize = 13.sp, lineHeight = 19.5.sp, fontWeight = FontWeight.Normal)
    val bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val label = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val eyebrow =
        TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.48.sp)
    val price = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    val input = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.17.sp)
    val stepperSymbol = TextStyle(fontSize = 24.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
}

/** Applies the Bricolage Grotesque display family to a brand style. */
@Composable
fun TextStyle.display(): TextStyle = copy(fontFamily = rusuhDisplayFontFamily())

/** Applies the Space Grotesk body family to a brand style. */
@Composable
fun TextStyle.body(): TextStyle = copy(fontFamily = rusuhBodyFontFamily())

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
