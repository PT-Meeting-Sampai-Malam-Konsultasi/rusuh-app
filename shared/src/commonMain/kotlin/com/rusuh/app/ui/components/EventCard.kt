package com.rusuh.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Event format shown as a secondary badge on the poster (Tour / Live / Festival). */
enum class EventFormat(val label: String) {
    TOUR("Tour"),
    LIVE("Live"),
    FESTIVAL("Festival"),
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

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(if (disabled) DISABLED_ALPHA else 1f)
                .semantics(mergeDescendants = true) { contentDescription = cardDescription },
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        enabled = clickable,
        onClick = { onClick?.invoke() },
    ) {
        PosterSlot(
            image = image,
            format = data.format,
            contentDescription = "Poster ${data.title}",
        )
        Column(modifier = Modifier.padding(16.dp)) {
            StatusBadge(status = data.status)
            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(8.dp))
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

/** Poster image / placeholder with a top-down darkening gradient and format badge. */
@Composable
private fun PosterSlot(
    image: Painter?,
    format: EventFormat,
    contentDescription: String,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT_RATIO)
                .clip(MaterialTheme.shapes.large),
    ) {
        if (image != null) {
            Image(
                painter = image,
                contentDescription = contentDescription,
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
        // Overlay gradient so overlaid chrome stays legible on any poster.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, OVERLAY_GRADIENT_END),
                        ),
                    ),
        )
        FormatBadge(
            format = format,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
        )
    }
}

@Composable
private fun FormatBadge(
    format: EventFormat,
    modifier: Modifier = Modifier,
) {
    Text(
        text = format.label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .semantics { contentDescription = "Format ${format.label}" },
    )
}

/** Status-specific supporting line below the main info (sold-out stats, countdown). */
@Composable
private fun StatusFooter(status: EventStatus) {
    val text = status.footerText() ?: return
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Supporting footer text for a status, or null when the status needs none.
 * Pure function — unit-tested directly.
 */
fun EventStatus.footerText(): String? =
    when (this) {
        EventStatus.Available -> null
        EventStatus.TemporarilyFull ->
            "Sisa tiket sedang dibayar pembeli lain. " +
                "Yang tidak jadi dibayar kembali dalam 10 menit."
        is EventStatus.SoldOut ->
            "Semua ${capacity.formatThousands()} tiket terjual dalam $soldOutInMinutes menit."
        is EventStatus.ComingSoon -> countdown
    }

/** Full accessibility description combining event identity and status. */
internal fun buildCardDescription(data: EventCardData): String {
    val base =
        "${data.title} ${data.subtitle}, ${data.format.label}, " +
            "${data.venue} ${data.city}, ${data.dateTime} ${data.timeZone.name}. " +
            "Status: ${data.status.badgeLabel()}"
    val footer = data.status.footerText()
    return if (footer != null) "$base. $footer" else base
}

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

private const val DISABLED_ALPHA = 0.5f
private const val POSTER_ASPECT_RATIO = 16f / 9f
private val OVERLAY_GRADIENT_END = Color(0x99000000)
