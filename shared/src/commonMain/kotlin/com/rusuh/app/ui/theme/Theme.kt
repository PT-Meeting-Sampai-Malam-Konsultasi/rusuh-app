package com.rusuh.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Root theme wrapper applying Rusuh color, typography and shape tokens. */
@Composable
fun RusuhTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RusuhLightColorScheme,
        typography = RusuhTypography,
        shapes = RusuhMaterialShapes,
        content = content,
    )
}
