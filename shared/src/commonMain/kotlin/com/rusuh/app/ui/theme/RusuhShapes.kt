package com.rusuh.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Shape tokens (corner radii). */
object RusuhShapes {
    val small = RoundedCornerShape(4.dp)
    val medium = RoundedCornerShape(8.dp)
    val large = RoundedCornerShape(16.dp)
}

internal val RusuhMaterialShapes =
    Shapes(
        small = RusuhShapes.small,
        medium = RusuhShapes.medium,
        large = RusuhShapes.large,
    )
