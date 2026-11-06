package com.rusuh.app.feature.home

import com.rusuh.app.ui.components.EventCardData
import com.rusuh.app.ui.components.EventFormat
import com.rusuh.app.ui.components.EventStatus
import com.rusuh.app.ui.components.TimeZoneLabel

/**
 * Maps an MVI [HomeEventItem] to the richer [EventCardData] that M1's `EventCard`
 * renders.
 *
 * M3 intentionally kept [HomeEventItem] minimal (id + title) and left the UI wiring
 * to M2, while M1's [EventCardData] carries the full display model (venue, schedule,
 * status, price). Until M4 lands the real API and a shared domain event type, this
 * screen only has an id and a title to show, so the remaining card fields fall back
 * to neutral placeholders.
 *
 * shortcut: placeholder venue/city/date/status until M4's API maps the real domain
 * fields; replace this mapper (not the screen) when the event model gains them.
 */
internal fun HomeEventItem.toEventCardData(): EventCardData =
    EventCardData(
        title = title,
        subtitle = "",
        format = EventFormat.LIVE,
        venue = "",
        city = "",
        dateTime = "",
        timeZone = TimeZoneLabel.WIB,
        status = EventStatus.Available,
    )
