package com.rusuh.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.event_card_price_from
import rusuh_app.shared.generated.resources.status_prefix

/** Event format shown in the card's accessibility description (Tour / Live / Festival). */
enum class EventFormat(val label: String) {
    TOUR("Tour"),
    LIVE("Live"),
    FESTIVAL("Festival"),

    /** Format not yet known (e.g. before the API resolves it); omitted from the description. */
    UNKNOWN(""),
}

/**
 * Display data for one event card. UI-facing view model: all strings are
 * already localized/formatted by the caller so the card stays clock- and
 * locale-agnostic.
 *
 * @param title main event name (e.g. "Langit Sore"); also the big poster word.
 * @param subtitle tagline shown after an em dash (e.g. "Senja Panjang Live").
 * @param format Tour / Live / Festival.
 * @param venue venue name (e.g. "Stadion Kridosono").
 * @param city city name (e.g. "Yogyakarta").
 * @param dateTime formatted date + time (e.g. "Sab, 9 Jan 2027 · 18.30").
 * @param timeZone display timezone label — WIB / WITA / WIT.
 * @param priceFrom formatted starting amount (e.g. "Rp 325.000"); the "From" prefix is
 *   localized by the card. Null to hide.
 * @param status availability status, drives the stamp, footer, action and disabled state.
 * @param posterTone poster colour way used while no poster image is supplied.
 * @param posterLabel short chip on the poster; falls back to [subtitle]'s first words when null.
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
    val posterTone: EventPosterTone = EventPosterTone.Blue,
    val posterLabel: String? = null,
)

/** Indonesian display timezones. */
enum class TimeZoneLabel { WIB, WITA, WIT }

/**
 * Event card for the home-page list (Figma mobile row: poster thumbnail on the
 * left; stamp, title, schedule, price and call-to-action on the right).
 *
 * Sold-out and ended events are non-clickable with a dimmed, greyscale poster;
 * every other status is clickable when [onClick] is non-null. The whole card is a
 * single semantic unit so screen readers announce event + status + schedule in
 * one pass.
 *
 * @param image optional poster painter; when null a branded placeholder poster is
 *   drawn. Kept as a [Painter] slot so no image-loading library is pulled into
 *   commonMain for this component.
 * @param onClick tap handler for the whole card; ignored for sold-out / ended.
 * @param onActionClick tap handler for the call-to-action button (e.g. "Ingatkan
 *   saya"); falls back to [onClick] when null.
 */
@Composable
fun EventCard(
    data: EventCardData,
    modifier: Modifier = Modifier,
    image: Painter? = null,
    onClick: (() -> Unit)? = null,
    onActionClick: (() -> Unit)? = null,
) {
    val disabled = data.status.isDisabled
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
            modifier = Modifier.padding(vertical = RusuhSpacing.xl),
            horizontalArrangement = Arrangement.spacedBy(RusuhSpacing.xl),
        ) {
            PosterThumb(data = data, image = image, dimmed = disabled)
            EventInfo(data = data, onActionClick = onActionClick ?: onClick)
        }
        HorizontalDivider(thickness = RusuhSpacing.hairline, color = RusuhColors.Hair)
    }
}

@Composable
private fun EventInfo(
    data: EventCardData,
    onActionClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val disabled = data.status.isDisabled
    val footers = data.status.footerTexts()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.sm),
    ) {
        StatusBadge(status = data.status)
        Text(
            text = if (data.subtitle.isBlank()) data.title else "${data.title} — ${data.subtitle}",
            style = MaterialTheme.typography.titleSmall,
            color = if (disabled) RusuhColors.Ink2 else RusuhColors.Ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = scheduleText(data),
            style = MaterialTheme.typography.bodySmall,
            color = if (disabled) RusuhColors.Ink3 else RusuhColors.Ink2,
        )
        data.priceFrom?.let { PriceLine(amount = it) }
        if (disabled) {
            // Closed events show their closing note in the price slot, muted.
            footers.forEach { FooterLine(text = it, color = RusuhColors.Ink3) }
        } else {
            ActionBlock(status = data.status, footers = footers, onActionClick = onActionClick)
        }
    }
}

/** Date/time with timezone on one line, venue on the next. Blank parts are skipped. */
internal fun scheduleText(data: EventCardData): String =
    listOf(
        if (data.dateTime.isNotBlank()) "${data.dateTime} ${data.timeZone.name}" else "",
        listOf(data.venue, data.city).filter { it.isNotBlank() }.joinToString(", "),
    ).filter { it.isNotBlank() }.joinToString("\n")

@Composable
private fun PriceLine(amount: String) {
    val prefix = stringResource(Res.string.event_card_price_from)
    Text(
        text =
            buildAnnotatedString {
                append(prefix)
                append(" ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(amount) }
            },
        style = MaterialTheme.typography.bodyMedium,
        color = RusuhColors.Ink,
    )
}

@Composable
private fun FooterLine(
    text: String,
    color: Color,
) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

/** Hairline-topped block with the status footer lines and the call-to-action. */
@Composable
private fun ActionBlock(
    status: EventStatus,
    footers: List<String>,
    onActionClick: (() -> Unit)?,
) {
    val action = status.cardAction()
    if (footers.isEmpty() && action == null) return
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = RusuhColors.Hair,
                        start = Offset.Zero,
                        end = Offset(size.width, 0f),
                        strokeWidth = RusuhSpacing.hairline.toPx(),
                    )
                }.padding(top = RusuhSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(RusuhSpacing.lg),
    ) {
        if (footers.isNotEmpty()) {
            Column {
                footers.forEach { FooterLine(text = it, color = RusuhColors.Ink2) }
            }
        }
        action?.let { ActionButton(label = stringResource(it.label), primary = it.primary, onClick = onActionClick) }
    }
}

/**
 * Full accessibility description combining the event's known identity fields and
 * status. Blank fields (e.g. before the API resolves venue/date) are skipped so
 * the reader never hears empty segments or stray separators.
 *
 * `@Composable` so status label + footers resolve from string resources; the
 * identity assembly is pure and stays covered by [buildCardIdentity].
 */
@Composable
internal fun buildCardDescription(data: EventCardData): String {
    val statusLabel = stringResource(data.status.badgeLabel())
    val footers = data.status.footerTexts()
    return buildString {
        append(buildCardIdentity(data))
        data.priceFrom?.let { append(". ${stringResource(Res.string.event_card_price_from)} $it") }
        append(". ")
        append(stringResource(Res.string.status_prefix, statusLabel))
        footers.forEach { append(". $it") }
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
