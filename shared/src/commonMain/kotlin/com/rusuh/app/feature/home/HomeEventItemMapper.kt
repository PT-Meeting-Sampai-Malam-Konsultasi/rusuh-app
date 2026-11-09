package com.rusuh.app.feature.home

import com.rusuh.app.ui.components.EventCardData

/**
 * Maps a [HomeEventItem] to the [EventCardData] that `EventCard` renders.
 *
 * A straight field-for-field copy: [HomeEventItem] already carries the display
 * model, so the mapper owns no defaults. A minimal item (id + title) keeps
 * [HomeEventItem]'s neutral defaults (on sale, unknown format, blank schedule) and
 * so never shows fabricated venue or date text.
 */
internal fun HomeEventItem.toEventCardData(): EventCardData =
    EventCardData(
        title = title,
        subtitle = subtitle,
        format = format,
        venue = venue,
        city = city,
        dateTime = dateTime,
        timeZone = timeZone,
        status = status,
        priceFrom = priceFrom,
        posterTone = posterTone,
    )
