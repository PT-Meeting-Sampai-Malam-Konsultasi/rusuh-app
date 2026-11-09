package com.rusuh.app.feature.home

import com.rusuh.app.ui.components.EventCardData

/**
 * Maps an MVI [HomeEventItem] to the richer [EventCardData] that M1's `EventCard`
 * renders.
 *
 * M3 intentionally kept [HomeEventItem] minimal (id + title) and left the UI wiring
 * to M2, while M1's [EventCardData] carries the full display model (venue, schedule,
 * status, price). Until M4 lands the real API and a shared domain event type, this
 * screen only has an id and a title to show. [EventCardData] requires non-null
 * `status` and `format`, so the mapper must supply some value for each; it chooses
 * the least content-bearing ones: [EventStatus.Available] (the only param-free
 * status that keeps the card enabled/clickable rather than dimming it),
 * [EventFormat.UNKNOWN] (renders no format badge, so nothing is mislabeled) and a
 * blank venue/city/date/subtitle/price so no fabricated schedule is shown.
 *
 * [HomeEventItem] now carries optional, defaulted display fields that pass straight
 * through here; an item built with only id + title still maps to the placeholders
 * above. The stub repository fills them with sample data (TODO(M4)).
 *
 * shortcut: placeholder status/format + blank detail fields until M4's API maps the
 * real domain fields; replace this mapper (not the screen) when the event model
 * gains them. If M1's EventCardData later makes these fields optional, drop the
 * placeholders here instead of inventing values.
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
    )
