package com.rusuh.app.feature.tickets

import com.rusuh.app.ui.components.TicketAttendeeUi
import com.rusuh.app.ui.components.TicketOrderUi
import com.rusuh.app.ui.components.TicketPoster

// Placeholder data copied from the Figma "Event Purchased" frame. Replaced by the
// my-tickets API in TICKET-00-M4.

internal fun placeholderUpcomingOrders(): List<TicketOrderUi> =
    listOf(
        TicketOrderUi(
            orderId = "RSH-26-071205",
            eventTitle = "Pelita – Tur Kota Lama",
            dateVenue = "Jum, 4 Des 2026 · 19.30 WIB · Lapangan Simpang Lima, Semarang",
            posterLabel = "PELITA",
            poster = TicketPoster.Pink,
            attendees =
                listOf(
                    TicketAttendeeUi("Festival A", "Nadia Saputri"),
                    TicketAttendeeUi("Festival A", "Bagas Hidayat"),
                ),
            orderCode = "RSH-26-071205",
            totalLabel = "Rp 1.350.000",
            dayLabel = "4",
            monthLabel = "DES",
            countdownLabel = "25 hari lagi",
        ),
        TicketOrderUi(
            orderId = "RSH-26-084731",
            eventTitle = "Samudra – Tur Gelombang 2026",
            dateVenue = "Sab, 12 Des 2026 · 19.00 WIB · Stadion Utama GBK, Jakarta",
            posterLabel = "SAMUDRA",
            poster = TicketPoster.Blue,
            attendees =
                listOf(
                    TicketAttendeeUi("Festival B", "Nadia Saputri"),
                    TicketAttendeeUi("Festival B", "Rina Wulandari"),
                    TicketAttendeeUi("Tribun Atas", "Dimas Pratama"),
                ),
            orderCode = "RSH-26-084731",
            totalLabel = "Rp 2.180.000",
            dayLabel = "12",
            monthLabel = "DES",
            countdownLabel = "33 hari lagi",
        ),
    )

internal fun placeholderPastOrders(): List<TicketOrderUi> =
    listOf(
        TicketOrderUi(
            orderId = "RSH-26-052118",
            eventTitle = "Kelana – Tur Pulau",
            dateVenue = "Sab, 31 Okt 2026 · 19.00 WITA · Lapangan Puputan, Denpasar",
            posterLabel = "KELANA",
            poster = TicketPoster.Grey,
            attendees = listOf(TicketAttendeeUi("Festival", "Nadia Saputri")),
            orderCode = "RSH-26-052118",
            totalLabel = "Rp 450.000",
            dayLabel = "31",
            monthLabel = "OKT",
            isPast = true,
        ),
    )
