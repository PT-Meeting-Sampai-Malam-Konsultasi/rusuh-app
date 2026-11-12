package com.rusuh.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rusuh.app.qr.QrEcc
import com.rusuh.app.qr.QrMatrix
import kotlin.math.floor

/** Light border around the symbol, in modules, required by the QR spec for reliable scanning. */
internal const val QR_QUIET_ZONE_MODULES = 4

/** Modules across one side including the quiet zone on both sides. */
internal fun QrMatrix.modulesWithQuietZone(): Int = size + 2 * QR_QUIET_ZONE_MODULES

/**
 * Draws [payload] as a QR code (ECC level M) on a light square with the 4 module quiet zone.
 *
 * Dark modules on a light background in every theme: scanners expect dark-on-light, so the colors are
 * fixed and ignore dark mode. The content is exactly [payload]; nothing is logged. A payload that cannot
 * be encoded (longer than a version 40 symbol) draws the empty light square rather than crashing.
 *
 * @param contentDescription what TalkBack announces, e.g. "QR code for ticket SMD-FB-04827" (never the
 *   payload itself).
 * @param size outer side length including the quiet zone.
 */
@Composable
fun QrCode(
    payload: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 176.dp,
) {
    val matrix = remember(payload) { runCatching { QrMatrix.encode(payload, QrEcc.Medium) }.getOrNull() }
    Canvas(
        modifier =
            modifier
                .size(size)
                .background(QR_LIGHT)
                .semantics { this.contentDescription = contentDescription },
    ) {
        if (matrix == null) return@Canvas
        val total = matrix.modulesWithQuietZone()
        // Whole-pixel modules keep the edges crisp, which scans better than fractional ones.
        val cell = maxOf(1f, floor(this.size.minDimension / total))
        val origin = (this.size.minDimension - cell * total) / 2f
        for (y in 0 until matrix.size) {
            var x = 0
            while (x < matrix.size) {
                if (!matrix.isDark(x, y)) {
                    x++
                    continue
                }
                val start = x
                while (x < matrix.size && matrix.isDark(x, y)) x++
                drawRect(
                    color = QR_DARK,
                    topLeft =
                        Offset(
                            origin + (start + QR_QUIET_ZONE_MODULES) * cell,
                            origin + (y + QR_QUIET_ZONE_MODULES) * cell,
                        ),
                    size = Size((x - start) * cell, cell),
                )
            }
        }
    }
}

private val QR_LIGHT = Color.White
private val QR_DARK = Color.Black
