package com.rusuh.app.feature.home

import com.rusuh.app.ui.components.EventFormat
import com.rusuh.app.ui.components.EventStatus
import com.rusuh.app.ui.components.TimeZoneLabel

/**
 * Data seam for the Home screen.
 *
 * This interface is the contract M4 (API integration) will implement against the
 * real backend. Until then [StubHomeRepository] returns four sample events so the MVI
 * layer and the placeholder UI can be built and tested independently.
 */
interface HomeRepository {
    /** Fetch the list of events whose tickets are sold online. */
    suspend fun getEvents(): List<HomeEventItem>
}

/**
 * Placeholder implementation used until M4 wires the real API.
 *
 * TODO(M4): replace with the real network-backed implementation. These four sample
 * events exist only so the Home UI can be compared against the Figma mockup; they
 * cover every status (Available / TemporarilyFull / SoldOut / ComingSoon) and every
 * format (Tour / Live / Festival).
 */
class StubHomeRepository : HomeRepository {
    override suspend fun getEvents(): List<HomeEventItem> = SAMPLE_EVENTS
}

// TODO(M4): sample data only -- delete together with StubHomeRepository.
private val SAMPLE_EVENTS =
    listOf(
        HomeEventItem(
            id = "sample-1",
            title = "Langit Sore",
            subtitle = "Senja Panjang Live",
            format = EventFormat.LIVE,
            venue = "Stadion Kridosono",
            city = "Yogyakarta",
            dateTime = "Sab, 9 Jan 2027 · 18.30",
            timeZone = TimeZoneLabel.WIB,
            status = EventStatus.Available,
            priceFrom = "Mulai Rp 325.000",
        ),
        HomeEventItem(
            id = "sample-2",
            title = "Nusantara Bergema",
            subtitle = "Tur Lima Kota",
            format = EventFormat.TOUR,
            venue = "GWK Cultural Park",
            city = "Bali",
            dateTime = "Jum, 15 Jan 2027 · 19.00",
            timeZone = TimeZoneLabel.WITA,
            status = EventStatus.TemporarilyFull,
            priceFrom = "Mulai Rp 450.000",
        ),
        HomeEventItem(
            id = "sample-3",
            title = "Pesta Pantai Timur",
            subtitle = "Festival Musik Tiga Hari",
            format = EventFormat.FESTIVAL,
            venue = "Pantai Tanjung Bunga",
            city = "Makassar",
            dateTime = "Sab, 23 Jan 2027 · 15.00",
            timeZone = TimeZoneLabel.WITA,
            status = EventStatus.SoldOut(soldOutInMinutes = 41, capacity = 12_000),
        ),
        HomeEventItem(
            id = "sample-4",
            title = "Gema Papua",
            subtitle = "Malam Akustik",
            format = EventFormat.LIVE,
            venue = "GOR Cenderawasih",
            city = "Jayapura",
            dateTime = "Min, 7 Feb 2027 · 20.00",
            timeZone = TimeZoneLabel.WIT,
            status = EventStatus.ComingSoon(countdown = "7 hari 14 jam lagi"),
            priceFrom = "Mulai Rp 275.000",
        ),
    )
