package com.rusuh.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.event_card_poster_sold_out

/** Poster colour way, mirroring the hi-fi `.poster.p-*` variants. */
enum class EventPosterTone { Blue, Pink, Paper, Ink }

/** Poster palette for a tone: background, text, halftone dot, chip background and chip text. */
internal data class PosterPalette(
    val background: Color,
    val foreground: Color,
    val dots: Color,
    val chipBackground: Color,
    val chipForeground: Color,
)

internal fun EventPosterTone.palette(): PosterPalette =
    when (this) {
        EventPosterTone.Blue ->
            PosterPalette(RusuhColors.Blue, RusuhColors.Paper, RusuhColors.Pink, RusuhColors.Paper, RusuhColors.PinkInk)
        EventPosterTone.Pink ->
            PosterPalette(
                RusuhColors.Pink,
                RusuhColors.OnPrimary,
                RusuhColors.Blue,
                RusuhColors.Paper,
                RusuhColors.Blue,
            )
        EventPosterTone.Paper ->
            PosterPalette(
                RusuhColors.PaperCard,
                RusuhColors.Blue,
                RusuhColors.Blue.copy(alpha = HALFTONE_PAPER_ALPHA),
                RusuhColors.Blue,
                RusuhColors.Paper,
            )
        EventPosterTone.Ink ->
            PosterPalette(RusuhColors.Ink, RusuhColors.Paper, RusuhColors.Pink, RusuhColors.Paper, RusuhColors.Ink)
    }

/**
 * Poster thumbnail with a hard offset shadow, halftone dots, big title word and a
 * subtitle chip. Sold-out events get a greyscale, dimmed poster with a "HABIS" tag.
 *
 * Purely visual: the parent card already announces identity, format and status as
 * one merged description, so nothing here carries its own contentDescription.
 */
@Composable
internal fun PosterThumb(
    data: EventCardData,
    image: Painter?,
    dimmed: Boolean,
) {
    val palette = data.posterTone.palette()
    val shadow = RusuhSpacing.posterShadow
    Box(modifier = Modifier.padding(end = shadow, bottom = shadow)) {
        Box(
            modifier =
                Modifier
                    .width(RusuhSpacing.posterThumbWidth)
                    .aspectRatio(POSTER_ASPECT_RATIO)
                    .drawBehind {
                        val o = shadow.toPx()
                        drawRect(color = RusuhColors.Ink, topLeft = Offset(o, o), size = size)
                    }.then(if (dimmed) Modifier.greyscale().alpha(POSTER_DIM_ALPHA) else Modifier)
                    .background(palette.background, RectangleShape),
        ) {
            if (image != null) {
                Image(
                    painter = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                HalftoneDots(color = palette.dots)
                PosterTitle(data = data, palette = palette)
            }
            if (data.status is EventStatus.SoldOut) SoldOutTag(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun HalftoneDots(color: Color) {
    Spacer(
        modifier =
            Modifier.fillMaxSize().drawBehind {
                val step = HALFTONE_STEP.toPx()
                val radius = HALFTONE_RADIUS.toPx()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        drawCircle(color = color, radius = radius, center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
            },
    )
}

@Composable
private fun PosterTitle(
    data: EventCardData,
    palette: PosterPalette,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(RusuhSpacing.md),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = data.title.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            fontSize = POSTER_TITLE_SIZE,
            lineHeight = POSTER_TITLE_LINE_HEIGHT,
            color = palette.foreground,
            maxLines = 3,
            overflow = TextOverflow.Clip,
        )
        val chip = data.posterLabel ?: data.subtitle.substringBefore(" Live").trim()
        if (chip.isNotBlank()) {
            Spacer(Modifier.height(RusuhSpacing.sm))
            Text(
                text = chip,
                style = MaterialTheme.typography.labelSmall,
                color = palette.chipForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .graphicsLayer { rotationZ = POSTER_CHIP_ROTATION }
                        .background(palette.chipBackground, RectangleShape)
                        .padding(horizontal = RusuhSpacing.xs, vertical = RusuhSpacing.xs),
            )
        }
    }
}

@Composable
private fun SoldOutTag(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.event_card_poster_sold_out),
        style = MaterialTheme.typography.titleSmall,
        color = RusuhColors.Ink,
        modifier =
            modifier
                .graphicsLayer { rotationZ = SOLD_OUT_TAG_ROTATION }
                .background(RusuhColors.Paper, RectangleShape)
                .border(RusuhSpacing.stampBorder, RusuhColors.Ink, RectangleShape)
                .padding(horizontal = RusuhSpacing.sm, vertical = RusuhSpacing.xs),
    )
}

/** Renders the content desaturated, like the hi-fi `.poster.is-dim { filter: grayscale(1) }`. */
private fun Modifier.greyscale(): Modifier =
    drawWithContent {
        val paint =
            Paint().apply {
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
            }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(size.toRect(), paint)
            drawContent()
            canvas.restore()
        }
    }

private const val POSTER_ASPECT_RATIO = 4f / 5f
private const val POSTER_DIM_ALPHA = 0.85f
private const val HALFTONE_PAPER_ALPHA = 0.35f
private const val POSTER_CHIP_ROTATION = -2f
private const val SOLD_OUT_TAG_ROTATION = -12f
private val HALFTONE_STEP = 14.dp
private val HALFTONE_RADIUS = 2.4.dp
private val POSTER_TITLE_SIZE = 26.sp
private val POSTER_TITLE_LINE_HEIGHT = 22.sp
