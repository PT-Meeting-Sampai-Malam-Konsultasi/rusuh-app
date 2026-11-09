package com.rusuh.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * Event card for the home-page list.
 *
 * A sold-out event is non-clickable and dimmed; every other status is clickable
 * when [onClick] is non-null. The whole card is a single semantic unit so screen
 * readers announce event + status + schedule in one pass.
 *
 * @param image optional poster painter; when null a branded placeholder box is
 *   drawn. Kept as a [Painter] slot so no image-loading library is pulled into
 *   commonMain for this component — callers supply whatever loader they use.
 * @param onClick tap handler; ignored (and card marked disabled) for sold-out.
 */
@Composable
fun EventCard(
    data: EventCardData,
    modifier: Modifier = Modifier,
    image: Painter? = null,
    onClick: (() -> Unit)? = null,
) {
    val disabled = data.status is EventStatus.SoldOut
    val clickable = onClick != null && !disabled
    val cardDescription = buildCardDescription(data)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(if (disabled) RusuhSpacing.DISABLED_ALPHA else 1f)
                // Hard (zero-blur) offset shadow drawn behind the bordered card.
                .padding(end = RusuhSpacing.hardShadowOffset, bottom = RusuhSpacing.hardShadowOffset)
                .drawBehind {
                    val offset = RusuhSpacing.hardShadowOffset.toPx()
                    val radius = RusuhShapes.cardRadius.toPx()
                    drawRoundRect(
                        color = RusuhColors.Ink,
                        topLeft = Offset(offset, offset),
                        size = size,
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
                .clip(RusuhShapes.card)
                .background(MaterialTheme.colorScheme.surface)
                .border(RusuhSpacing.inkBorder, RusuhColors.Ink, RusuhShapes.card)
                .clickable(enabled = clickable, role = Role.Button) { onClick?.invoke() }
                // clickable(enabled = false) already marks the node disabled.
                .semantics(mergeDescendants = true) { contentDescription = cardDescription },
    ) {
        PosterSlot(
            image = image,
            format = data.format,
        )
        // Ink rule separating poster and body.
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(RusuhSpacing.inkBorder)
                .background(RusuhColors.Ink),
        )
        Column(modifier = Modifier.padding(RusuhSpacing.xl)) {
            StatusBadge(status = data.status)
            Spacer(Modifier.height(RusuhSpacing.md))
            Text(
                text = data.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = data.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(RusuhSpacing.md))
            Text(
                text = "${data.dateTime} ${data.timeZone.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${data.venue}, ${data.city}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            data.priceFrom?.let { price ->
                Spacer(Modifier.height(RusuhSpacing.md))
                Text(
                    text = price,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            StatusFooter(status = data.status)
        }
    }
}

/**
 * Poster image / placeholder with a top-down darkening scrim and format badge.
 *
 * Purely visual: the parent card already announces the event identity, format
 * and status as one merged description, so the poster and format badge carry no
 * contentDescription (null image = decorative) to avoid a redundant,
 * double-read announcement.
 */
@Composable
private fun PosterSlot(
    image: Painter?,
    format: EventFormat,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT_RATIO),
    ) {
        if (image != null) {
            Image(
                painter = image,
                // decorative; the merged card description already covers it
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // No image library in the project yet: a branded placeholder box
            // rather than pulling in Coil/Kamel for a single slot.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
            )
        }
        // Scrim so overlaid chrome stays legible on any poster.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, RusuhColors.PosterScrim),
                        ),
                    ),
        )
        // No badge for an unknown format (blank label), so a card never shows an
        // empty pill before the real format is resolved.
        if (format.label.isNotEmpty()) {
            FormatBadge(
                format = format,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(RusuhSpacing.lg),
            )
        }
    }
}

@Composable
private fun FormatBadge(
    format: EventFormat,
    modifier: Modifier = Modifier,
) {
    // Diagonal tag: rotated, ink-outlined pill.
    Text(
        text = format.label.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = RusuhColors.OnFormatBadge,
        modifier =
            modifier
                .rotate(RusuhSpacing.FORMAT_BADGE_ROTATION)
                .clip(RusuhShapes.small)
                .background(RusuhColors.FormatBadge)
                .border(RusuhSpacing.inkBorder, RusuhColors.Ink, RusuhShapes.small)
                .padding(horizontal = RusuhSpacing.md, vertical = RusuhSpacing.xs),
    )
}

/** Status-specific supporting line below the main info (sold-out stats, countdown). */
@Composable
private fun StatusFooter(status: EventStatus) {
    val text = status.footerText() ?: return
    Spacer(Modifier.height(RusuhSpacing.md))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

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

private const val POSTER_ASPECT_RATIO = 16f / 9f
