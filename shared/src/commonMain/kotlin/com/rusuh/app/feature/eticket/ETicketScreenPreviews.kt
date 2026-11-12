@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.eticket

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.rusuh.app.ui.theme.RusuhTheme

// Preview-style sample composables (no @Preview: commonMain has no uiTooling-preview). Synthetic data only.

private const val SAMPLE_DOORS_OPEN = 1_797_066_000_000L // Sat 12 Dec 2026 16:00 WIB
private const val SAMPLE_PAID_AT = 1_794_200_820_000L // 12:07 WIB

private val SAMPLE_TICKETS =
    listOf(
        ETicket(
            ticketId = "tk_01JB2YBA1",
            ticketCode = "SMD-FB-04827",
            categoryName = "Festival B",
            seating = Seating.Standing,
            entryGate = "Gerbang 5",
            holderName = "Nadia Saputri",
            nikMasked = "3174 •••• •••• 0004",
            qrPayload = "RSH1.SMD-FB-04827.Q2hlY2tJbi1zaWduZWQ",
        ),
        ETicket(
            ticketId = "tk_01JB2YBA2",
            ticketCode = "SMD-FB-04828",
            categoryName = "Festival B",
            seating = Seating.Standing,
            entryGate = "Gerbang 5",
            holderName = "Rina Wulandari",
            nikMasked = "3201 •••• •••• 0017",
            qrPayload = "RSH1.SMD-FB-04828.Q2hlY2tJbi1zaWduZWQ",
        ),
        ETicket(
            ticketId = "tk_01JB2YBA3",
            ticketCode = "SMD-TA-11902",
            categoryName = "Tribun Atas",
            seating = Seating.Seated,
            entryGate = "Gerbang 9",
            holderName = "Dimas Pratama",
            nikMasked = "3273 •••• •••• 0002",
            qrPayload = "RSH1.SMD-TA-11902.Q2hlY2tJbi1zaWduZWQ",
        ),
    )

internal fun sampleETicketOrder(
    ticketCount: Int = 3,
    long: Boolean = false,
): ETicketOrder =
    ETicketOrder(
        orderId = "RSH-26-084731",
        eventName = if (long) "SAMUDRA GELOMBANG TUR 2026" else "SAMUDRA",
        venueShort = if (long) "Stadion Utama Gelora Bung Karno" else "GBK",
        doorsOpenEpochMillis = SAMPLE_DOORS_OPEN,
        paidAtEpochMillis = SAMPLE_PAID_AT,
        zone = EventZone.Wib,
        totalLabel = "Rp 2.180.000",
        paymentMethod = "QRIS",
        contactEmail = if (long) "nadia.saputri.dengan.nama.panjang@example.com" else "nadia.saputri@example.com",
        tickets =
            SAMPLE_TICKETS.take(ticketCount).map {
                if (long) {
                    it.copy(
                        categoryName = "Tribun Atas Premium Sisi Barat",
                        entryGate = "Gerbang Utama Barat 12",
                    )
                } else {
                    it
                }
            },
    )

@Composable
internal fun ETicketThreeTicketsPreview() {
    RusuhTheme { ETicketScreen(state = ETicketUiState.Content(sampleETicketOrder(3).toUi())) }
}

@Composable
internal fun ETicketOneTicketPreview() {
    RusuhTheme { ETicketScreen(state = ETicketUiState.Content(sampleETicketOrder(1).toUi())) }
}

@Composable
internal fun ETicketQrErrorPreview() {
    val order =
        sampleETicketOrder(1).copy(
            tickets =
                sampleETicketOrder(1).tickets.map {
                    it.copy(qrPayload = "x".repeat(3000))
                },
        )
    RusuhTheme { ETicketScreen(state = ETicketUiState.Content(order.toUi())) }
}

@Composable
internal fun ETicketLongNamesPreview() {
    RusuhTheme { ETicketScreen(state = ETicketUiState.Content(sampleETicketOrder(2, long = true).toUi())) }
}

/** 2x font scale: everything wraps instead of clipping. */
@Composable
internal fun ETicketLargeFontPreview() {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
        RusuhTheme { ETicketScreen(state = ETicketUiState.Content(sampleETicketOrder(3).toUi())) }
    }
}

@Composable
internal fun ETicketLoadingPreview() {
    RusuhTheme { ETicketScreen(state = ETicketUiState.Loading) }
}

@Composable
internal fun ETicketErrorPreview() {
    RusuhTheme { ETicketScreen(state = ETicketUiState.Error) }
}

@Composable
internal fun ETicketNotFoundPreview() {
    RusuhTheme { ETicketScreen(state = ETicketUiState.NotFound) }
}
