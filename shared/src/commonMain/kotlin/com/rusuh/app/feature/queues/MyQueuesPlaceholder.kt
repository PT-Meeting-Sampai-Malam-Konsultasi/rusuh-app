package com.rusuh.app.feature.queues

import com.rusuh.app.ui.components.QueueListItemUi
import com.rusuh.app.ui.components.QueueListPhase

/**
 * Static placeholder queues for the My Queues (Antrian) screen (FR-APP-01-M2).
 *
 * UI-only sample data so the stateless screen renders a realistic list before
 * M3 wires the ViewModel and M4 the API. Covers the phase variants the row can
 * show (live, your-turn, open, paused, closed) so the placeholder exercises the
 * whole [QueueListItem] surface.
 *
 * shortcut: hard-coded sample; swap for live data once M3/M4 land.
 */
fun placeholderQueues(): List<QueueListItemUi> =
    listOf(
        QueueListItemUi(
            eventId = "evt-samudra",
            title = "Samudra — Tur Gelombang 2026",
            dateVenue = "12 Des 2026 · Stadion Madya, Jakarta",
            position = 128,
            nowServing = 102,
            phase = QueueListPhase.Live,
            updatedAgoSeconds = 3,
        ),
        QueueListItemUi(
            eventId = "evt-nyala",
            title = "Nyala Fest",
            dateVenue = "20 Des 2026 · GOR Saparua, Bandung",
            position = 4,
            nowServing = 1,
            phase = QueueListPhase.YourTurn,
            updatedAgoSeconds = 1,
        ),
        QueueListItemUi(
            eventId = "evt-rimba",
            title = "Rimba Raya Showcase",
            dateVenue = "4 Jan 2027 · Lapangan Banteng, Jakarta",
            position = 532,
            nowServing = null,
            phase = QueueListPhase.QueueOpen,
            updatedAgoSeconds = 8,
        ),
        QueueListItemUi(
            eventId = "evt-gema",
            title = "Gema Akustik",
            dateVenue = "18 Jan 2027 · Taman Budaya, Yogyakarta",
            position = 76,
            nowServing = 90,
            phase = QueueListPhase.Paused,
            updatedAgoSeconds = 25,
        ),
        QueueListItemUi(
            eventId = "evt-pasar",
            title = "Pasar Malam Senja",
            dateVenue = "2 Feb 2027 · Pantai Losari, Makassar",
            position = 2041,
            nowServing = null,
            phase = QueueListPhase.Closed,
            updatedAgoSeconds = 120,
        ),
    )
