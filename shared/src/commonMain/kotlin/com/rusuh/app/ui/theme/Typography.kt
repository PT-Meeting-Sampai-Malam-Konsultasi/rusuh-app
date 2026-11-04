package com.rusuh.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Text style tokens. */
object RusuhTextStyles {
    val h1 = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
    val h2 = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
    val body1 = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val body2 = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val button = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
}

internal val RusuhTypography = Typography(
    headlineLarge = RusuhTextStyles.h1,
    headlineMedium = RusuhTextStyles.h2,
    bodyLarge = RusuhTextStyles.body1,
    bodyMedium = RusuhTextStyles.body2,
    bodySmall = RusuhTextStyles.caption,
    labelLarge = RusuhTextStyles.button,
)
