package com.rusuh.app.feature.home

import com.rusuh.app.ui.components.EventFormat
import com.rusuh.app.ui.components.EventPosterTone
import com.rusuh.app.ui.components.EventStatus
import com.rusuh.app.ui.components.TimeZoneLabel

/**
 * Sample events covering every Figma event state (coming soon, waiting room open,
 * on sale, almost gone, temporarily full, sold out, ended) for previews and tests.
 * Copy follows the Figma / hi-fi mobile frame. Not wired to any data source.
 */
internal fun homePreviewEvents(): List<HomeEventItem> = previewOpenEvents() + previewClosedEvents()

private fun previewOpenEvents(): List<HomeEventItem> =
    listOf(
        HomeEventItem(
            id = "langit-sore",
            title = "Langit Sore",
            subtitle = "Senja Panjang Live",
            format = EventFormat.LIVE,
            venue = "Stadion Kridosono",
            city = "Yogyakarta",
            dateTime = "Sab, 9 Jan 2027 · 18.30",
            priceFrom = "Rp 325.000",
            status = EventStatus.ComingSoon(countdown = "7 hari 14 jam lagi", opensAt = "Jum, 30 Okt · 10.00 WIB"),
            posterTone = EventPosterTone.Pink,
        ),
        HomeEventItem(
            id = "samudra",
            title = "Samudra",
            subtitle = "Tur Gelombang 2026",
            format = EventFormat.TOUR,
            venue = "Stadion Utama GBK",
            city = "Jakarta",
            dateTime = "Sab, 12 Des 2026 · 19.00",
            priceFrom = "Rp 450.000",
            status = EventStatus.WaitingRoomOpen,
            posterTone = EventPosterTone.Blue,
        ),
        HomeEventItem(
            id = "nada-pantai",
            title = "Nada Pantai",
            subtitle = "Festival Musim Panas",
            format = EventFormat.FESTIVAL,
            venue = "Pantai Padang",
            city = "Padang",
            dateTime = "Sab, 16 Jan 2027 · 15.00",
            priceFrom = "Rp 200.000",
            status = EventStatus.OnSale,
            posterTone = EventPosterTone.Blue,
        ),
        HomeEventItem(
            id = "gema-malam",
            title = "Gema Malam",
            subtitle = "Konser Akustik",
            format = EventFormat.LIVE,
            venue = "Balai Sarbini",
            city = "Jakarta",
            dateTime = "Jum, 20 Nov 2026 · 20.00",
            priceFrom = "Rp 350.000",
            status = EventStatus.AlmostGone,
            posterTone = EventPosterTone.Ink,
        ),
    )

private fun previewClosedEvents(): List<HomeEventItem> =
    listOf(
        HomeEventItem(
            id = "rimba",
            title = "Rimba",
            subtitle = "Pesta Akar 2026",
            format = EventFormat.FESTIVAL,
            venue = "GOR Saparua",
            city = "Bandung",
            dateTime = "Min, 22 Nov 2026 · 16.00",
            priceFrom = "Rp 275.000",
            status = EventStatus.TemporarilyFull,
            posterTone = EventPosterTone.Paper,
        ),
        HomeEventItem(
            id = "deru",
            title = "Deru",
            subtitle = "Malam Mesin",
            format = EventFormat.LIVE,
            venue = "Jatim Expo",
            city = "Surabaya",
            dateTime = "Jum, 4 Des 2026 · 20.00",
            status = EventStatus.SoldOut(soldOutInMinutes = 41, capacity = 12_000),
            posterTone = EventPosterTone.Ink,
        ),
        HomeEventItem(
            id = "kelana",
            title = "Kelana",
            subtitle = "Tur Pulau",
            format = EventFormat.TOUR,
            venue = "Lapangan Puputan",
            city = "Denpasar",
            dateTime = "Sab, 31 Okt 2026 · 19.00",
            timeZone = TimeZoneLabel.WITA,
            status = EventStatus.Ended(closedOn = "29 Okt"),
            posterTone = EventPosterTone.Blue,
        ),
    )
