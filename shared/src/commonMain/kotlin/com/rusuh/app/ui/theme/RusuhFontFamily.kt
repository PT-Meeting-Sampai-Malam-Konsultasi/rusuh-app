package com.rusuh.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import rusuh_app.shared.generated.resources.BricolageGrotesque
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.SpaceGrotesk

/*
 * Brand font families, backed by Compose Multiplatform resource fonts.
 *
 * These accessors are @Composable because org.jetbrains.compose.resources.Font loads the bundled
 * TTF within a composition. Build them inside a composable (e.g. the theme) and feed the result
 * into the typography.
 *
 * Both TTFs are single-file SIL OFL variable fonts. Each weight the theme typography requests
 * (Normal/Medium/SemiBold/Bold) is registered against the same resource with its FontWeight, so
 * FontWeight lookups resolve to a declared face instead of collapsing to one default instance;
 * the variable font supplies the glyphs for each.
 * shortcut: weights share one variable-font file. Swap in per-weight static TTFs here if a target
 * renders only the default instance or the design needs exact static cuts.
 */

private val displayWeights = listOf(FontWeight.SemiBold, FontWeight.Bold)
private val bodyWeights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/** Display / heading font family: Bricolage Grotesque. */
@Composable
fun rusuhDisplayFontFamily(): FontFamily =
    FontFamily(displayWeights.map { Font(Res.font.BricolageGrotesque, weight = it) })

/** Body / UI text font family: Space Grotesk. */
@Composable
fun rusuhBodyFontFamily(): FontFamily = FontFamily(bodyWeights.map { Font(Res.font.SpaceGrotesk, weight = it) })
