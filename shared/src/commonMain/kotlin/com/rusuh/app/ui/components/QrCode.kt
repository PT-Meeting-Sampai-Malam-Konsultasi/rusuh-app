package com.rusuh.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.rusuh.app.ui.theme.RusuhBrandColors
import com.rusuh.app.ui.theme.RusuhBrandTextStyles
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.body
import kotlin.math.floor

/** Light border around the symbol, in modules, required by the QR spec for reliable scanning. */
internal const val QR_QUIET_ZONE_MODULES = 4

/** Modules across one side including the quiet zone on both sides. */
internal fun QrMatrix.modulesWithQuietZone(): Int = size + 2 * QR_QUIET_ZONE_MODULES

internal sealed interface QrEncodingResult {
    data class Encoded(val matrix: QrMatrix) : QrEncodingResult

    data object Failed : QrEncodingResult
}

internal fun encodeQr(payload: String): QrEncodingResult =
    runCatching { QrMatrix.encode(payload, QrEcc.Medium) }
        .fold(QrEncodingResult::Encoded) { QrEncodingResult.Failed }

/**
 * Draws [payload] as a QR code (ECC level M) on a light square with the 4 module quiet zone.
 *
 * Dark modules on a light background in every theme: scanners expect dark-on-light, so the colors are
 * fixed and ignore dark mode. The content is exactly [payload]; nothing is logged. A payload that cannot
 * be encoded, an explicit error message appears in place of the symbol.
 *
 * @param contentDescription what TalkBack announces, e.g. "QR code for ticket SMD-FB-04827" (never the
 *   payload itself).
 * @param size outer side length including the quiet zone.
 */
@Composable
fun QrCode(
    payload: String,
    contentDescription: String,
    errorMessage: String,
    modifier: Modifier = Modifier,
    size: Dp = 176.dp,
) {
    val matrix = remember(payload) { encodeQr(payload) }
    val encodedMatrix = (matrix as? QrEncodingResult.Encoded)?.matrix
    if (encodedMatrix == null) {
        Box(
            modifier =
                modifier
                    .size(size)
                    .background(QR_LIGHT)
                    .semantics { this.contentDescription = errorMessage },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                errorMessage,
                modifier = Modifier.fillMaxSize().padding(RusuhSpacing.sm),
                style = RusuhBrandTextStyles.label.body(),
                color = RusuhBrandColors.Ink,
            )
        }
        return
    }
    Canvas(
        modifier =
            modifier
                .size(size)
                .background(QR_LIGHT)
                .semantics { this.contentDescription = contentDescription },
    ) {
        val total = encodedMatrix.modulesWithQuietZone()
        // Whole-pixel modules keep the edges crisp, which scans better than fractional ones.
        val cell = maxOf(1f, floor(this.size.minDimension / total))
        val origin = (this.size.minDimension - cell * total) / 2f
        for (y in 0 until encodedMatrix.size) {
            var x = 0
            while (x < encodedMatrix.size) {
                if (!encodedMatrix.isDark(x, y)) {
                    x++
                    continue
                }
                val start = x
                while (x < encodedMatrix.size && encodedMatrix.isDark(x, y)) x++
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
