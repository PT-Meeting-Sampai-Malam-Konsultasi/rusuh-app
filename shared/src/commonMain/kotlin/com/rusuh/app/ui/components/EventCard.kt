package com.rusuh.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhShapes
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.event_footer_sold_out
import rusuh_app.shared.generated.resources.event_footer_temporarily_full
import rusuh_app.shared.generated.resources.status_passthrough
import rusuh_app.shared.generated.resources.status_prefix
import rusuh_app.shared.generated.resources.status_sold_out

/** Event format shown as a secondary badge on the poster (Tour / Live / Festival). */
enum class EventFormat(val label: String) {
    TOUR("Tour"),
    LIVE("Live"),
    FESTIVAL("Festival"),

    /** Format not yet known (e.g. before the API resolves it); renders no badge. */
    UNKNOWN(""),
}

/**
 * Display data for one event card. UI-facing view model: all strings are
 * already localized/formatted by the caller so the card stays clock- and
 * locale-agnostic.
 *
 * @param title main event name (e.g. "Langit Sore").
 * @param subtitle tagline shown after an em dash (e.g. "Senja Panjang Live").
 * @param format Tour / Live / Festival.
 * @param venue venue name (e.g. "Stadion Kridosono").
 * @param city city name (e.g. "Yogyakarta").
 * @param dateTime formatted date + time (e.g. "Sab, 9 Jan 2027 · 18.30").
 * @param timeZone display timezone label — WIB / WITA / WIT.
 * @param priceFrom formatted starting price (e.g. "Mulai Rp 325.000"); null to hide.
 * @param status availability status, drives the badge, footer and disabled state.
 */
data class EventCardData(
    val title: String,
    val subtitle: String,
    val format: EventFormat,
    val venue: String,
    val city: String,
    val dateTime: String,
    val timeZone: TimeZoneLabel,
    val status: EventStatus,
    val priceFrom: String? = null,
)

/** Indonesian display timezones. */
enum class TimeZoneLabel { WIB, WITA, WIT }

/**
 * Event row for the home-page list (hi-fi mobile layout): a tilted poster
 * thumbnail with a hard ink shadow on the left; stamp, title, schedule, price and
 * status footer on the right; a hairline underneath.
 *
 * A sold-out event is non-clickable and its poster is greyed; every other status is
 * clickable when [onClick] is non-null. The whole row is a single semantic unit so
 * screen readers announce event + status + schedule in one pass.
 *
 * @param image optional poster painter; when null a generated flat-colour poster
 *   is drawn. Kept as a [Painter] slot so no image-loading library is pulled into
 *   commonMain for this component.
 * @param posterTilt rotation in degrees of the poster thumbnail; the list alternates
 *   [RusuhSpacing.POSTER_TILT_ODD] / [RusuhSpacing.POSTER_TILT_EVEN].
 * @param onClick tap handler; ignored (and row marked disabled) for sold-out.
 */
@Composable
fun EventCard(
    data: EventCardData,
    modifier: Modifier = Modifier,
    image: Painter? = null,
    onClick: (() -> Unit)? = null,
    posterTilt: Float = RusuhSpacing.POSTER_TILT_ODD,
) {
    val disabled = data.status is EventStatus.SoldOut
    val clickable = onClick != null && !disabled
    val cardDescription = buildCardDescription(data)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = cardDescription }
                .clickable(enabled = clickable, role = Role.Button) { onClick?.invoke() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = RusuhSpacing.eventRowPadding),
            horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.xl),
        ) {
            PosterThumb(data = data, image = image, tilt = posterTilt)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(RusuhSpacing.sm),
            ) {
                StatusBadge(status = data.status)
                Text(
                    text = if (data.subtitle.isBlank()) data.title else "${data.title} \u2014 ${data.subtitle}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = TITLE_SIZE,
                    lineHeight = TITLE_LINE_HEIGHT,
                    color = RusuhColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${data.dateTime} ${data.timeZone.name}".trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = DETAIL_SIZE,
                    color = RusuhColors.Ink2,
                )
                Text(
                    text = listOf(data.venue, data.city).filter { it.isNotBlank() }.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = DETAIL_SIZE,
                    color = RusuhColors.Ink2,
                )
                data.priceFrom?.let { price ->
                    Text(
                        text = boldAmount(price),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RusuhColors.Ink,
                    )
                }
                StatusFooter(status = data.status)
            }
        }
        HorizontalDivider(thickness = RusuhSpacing.hairline, color = RusuhColors.Hair)
    }
}

/** Bolds the amount after the currency marker: "Mulai Rp 325.000" -> "Mulai **Rp 325.000**". */
private fun boldAmount(price: String) =
    buildAnnotatedString {
        val at = price.indexOf("Rp")
        if (at < 0) {
            append(price)
        } else {
            append(price.substring(0, at))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(price.substring(at)) }
        }
    }

/** Flat poster fill and the colours of the title / subtitle chip drawn on it. */
private data class PosterPalette(val background: Color, val title: Color, val chip: Color, val chipText: Color)

private fun EventCardData.posterPalette(): PosterPalette =
    when {
        status is EventStatus.SoldOut ->
            PosterPalette(RusuhColors.Ink3, RusuhColors.Paper, RusuhColors.Paper, RusuhColors.Ink3)
        format == EventFormat.TOUR ->
            PosterPalette(RusuhColors.Blue, RusuhColors.Paper, RusuhColors.Paper, RusuhColors.PinkInk)
        format == EventFormat.LIVE ->
            PosterPalette(RusuhColors.Pink, Color.White, RusuhColors.Paper, RusuhColors.Blue)
        format == EventFormat.FESTIVAL ->
            PosterPalette(RusuhColors.Card, RusuhColors.Blue, RusuhColors.Blue, RusuhColors.Paper)
        else -> PosterPalette(RusuhColors.Ink, RusuhColors.Paper, RusuhColors.Paper, RusuhColors.Pink)
    }

/**
 * Poster thumbnail: flat colour (or [image]) with the event title set in the
 * condensed display face, a rotated subtitle chip, a hard ink shadow and a slight
 * tilt. Sold-out events get a rotated "sold out" stamp across the poster.
 *
 * Purely visual: the parent row already announces identity, format and status as
 * one merged description, so nothing here carries its own contentDescription.
 */
@Composable
private fun PosterThumb(
    data: EventCardData,
    image: Painter?,
    tilt: Float,
) {
    val palette = data.posterPalette()
    val shadow = RusuhSpacing.hardShadowOffset
    Box(
        modifier =
            Modifier
                .width(RusuhSpacing.posterThumbWidth)
                .aspectRatio(RusuhSpacing.POSTER_ASPECT)
                .rotate(tilt)
                .drawBehind {
                    val offset = shadow.toPx()
                    drawRect(RusuhColors.Ink, topLeft = Offset(offset, offset))
                }.background(palette.background)
                .clipToBounds(),
    ) {
        if (image != null) {
            Image(
                painter = image,
                // decorative; the merged row description already covers it
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, RusuhColors.PosterScrim))),
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(RusuhSpacing.md),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = data.title.uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                fontSize = POSTER_TITLE_SIZE,
                lineHeight = POSTER_TITLE_LINE_HEIGHT,
                fontWeight = FontWeight.Bold,
                color = palette.title,
                maxLines = POSTER_TITLE_MAX_LINES,
                overflow = TextOverflow.Clip,
            )
            if (data.subtitle.isNotBlank()) {
                Text(
                    text = data.subtitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = POSTER_CHIP_SIZE,
                    lineHeight = POSTER_CHIP_SIZE,
                    color = palette.chipText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .padding(top = RusuhSpacing.sm)
                            .rotate(RusuhSpacing.STAMP_ROTATION)
                            .background(palette.chip)
                            .padding(horizontal = RusuhSpacing.xs, vertical = RusuhSpacing.xxs),
                )
            }
        }
        if (data.status is EventStatus.SoldOut) {
            Text(
                text = stringResource(Res.string.status_sold_out).uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                fontSize = SOLD_STAMP_SIZE,
                lineHeight = SOLD_STAMP_SIZE,
                fontWeight = FontWeight.Bold,
                color = RusuhColors.Ink,
                maxLines = 1,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .rotate(SOLD_STAMP_ROTATION)
                        .background(RusuhColors.Paper)
                        .border(RusuhSpacing.stampBorder, RusuhColors.Ink, RusuhShapes.square)
                        .padding(horizontal = RusuhSpacing.sm, vertical = RusuhSpacing.xxs),
            )
        }
    }
}

/** Status-specific supporting line below the main info (sold-out stats, countdown), after a hairline. */
@Composable
private fun StatusFooter(status: EventStatus) {
    val text = status.footerText() ?: return
    Column(verticalArrangement = Arrangement.spacedBy(RusuhSpacing.md)) {
        Spacer(Modifier.height(RusuhSpacing.xxs))
        HorizontalDivider(thickness = RusuhSpacing.hairline, color = RusuhColors.Hair)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontSize = DETAIL_SIZE,
            color = RusuhColors.Ink2,
        )
    }
}

private val TITLE_SIZE = 18.sp
private val TITLE_LINE_HEIGHT = 20.sp
private val DETAIL_SIZE = 13.sp
private val POSTER_TITLE_SIZE = 26.sp
private val POSTER_TITLE_LINE_HEIGHT = 22.sp
private val POSTER_CHIP_SIZE = 11.sp
private val SOLD_STAMP_SIZE = 22.sp
private const val POSTER_TITLE_MAX_LINES = 3
private const val SOLD_STAMP_ROTATION = -12f

/**
 * Localized supporting footer text for a status, or null when the status needs
 * none. `@Composable` so it resolves the string resource at the call site; the
 * key/arg mapping itself is covered by the pure [footerSpec] below.
 */
@Composable
fun EventStatus.footerText(): String? {
    val spec = footerSpec() ?: return null
    // Resolve by arg count rather than a spread (avoids the array copy detekt
    // flags). Footer resources take 0, 1 or 2 args — enumerate them.
    return when (spec.args.size) {
        0 -> stringResource(spec.resource)
        1 -> stringResource(spec.resource, spec.args[0])
        else -> stringResource(spec.resource, spec.args[0], spec.args[1])
    }
}

/**
 * A status footer's string resource plus its format args (or null when the
 * status needs no footer). Pure function — unit-tested directly; the composable
 * [footerText] resolves it. ComingSoon carries its pre-formatted countdown
 * verbatim via a passthrough resource, so no localization is lost.
 */
internal fun EventStatus.footerSpec(): FooterSpec? =
    when (this) {
        EventStatus.Available -> null
        EventStatus.TemporarilyFull -> FooterSpec(Res.string.event_footer_temporarily_full)
        is EventStatus.SoldOut ->
            FooterSpec(Res.string.event_footer_sold_out, listOf(capacity.formatThousands(), soldOutInMinutes))
        is EventStatus.ComingSoon -> FooterSpec(Res.string.status_passthrough, listOf(countdown))
    }

/** Resource + format args for a status footer line. */
internal data class FooterSpec(
    val resource: StringResource,
    val args: List<Any> = emptyList(),
)

/**
 * Full accessibility description combining the event's known identity fields and
 * status. Blank fields (e.g. before the API resolves venue/date) are skipped so
 * the reader never hears empty segments or stray separators.
 *
 * `@Composable` so status label + footer resolve from string resources; the
 * identity assembly is pure and stays covered by [buildCardIdentity].
 */
@Composable
internal fun buildCardDescription(data: EventCardData): String {
    val statusLabel = stringResource(data.status.badgeLabel())
    val footer = data.status.footerText()
    return buildString {
        append(buildCardIdentity(data))
        data.priceFrom?.let { append(". $it") }
        append(". ")
        append(stringResource(Res.string.status_prefix, statusLabel))
        footer?.let { append(". $it") }
    }
}

/**
 * Pure (non-composable) identity segment of the card description: the locale-
 * agnostic, caller-formatted fields joined with blanks skipped. Unit-tested
 * directly.
 */
internal fun buildCardIdentity(data: EventCardData): String =
    listOf(
        data.title,
        data.subtitle,
        data.format.label,
        listOf(data.venue, data.city).filter { it.isNotBlank() }.joinToString(" "),
        // Timezone only reads alongside a date; a lone "WIB" would be noise.
        if (data.dateTime.isNotBlank()) "${data.dateTime} ${data.timeZone.name}" else "",
    ).filter { it.isNotBlank() }.joinToString(", ")

/** Groups digits with dots (Indonesian thousands separator): 12000 -> "12.000". */
internal fun Int.formatThousands(): String {
    val digits = toString()
    val sb = StringBuilder()
    for ((index, ch) in digits.withIndex()) {
        if (index > 0 && (digits.length - index) % 3 == 0) sb.append('.')
        sb.append(ch)
    }
    return sb.toString()
}
