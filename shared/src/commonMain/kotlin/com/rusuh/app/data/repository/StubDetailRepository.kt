package com.rusuh.app.data.repository

import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.AccountProfile
import com.rusuh.app.domain.model.Booking
import com.rusuh.app.domain.model.BookingRequest
import com.rusuh.app.domain.model.DetailOffering
import com.rusuh.app.domain.model.EventDetail
import com.rusuh.app.domain.model.SeatCategory
import com.rusuh.app.domain.repository.DetailRepository

/** Length of the order window in milliseconds (5 minutes). */
const val ORDER_WINDOW_MILLIS = 5 * 60 * 1_000L

/** Placeholder content (Figma "Samudra"); also feeds the previews. */
object StubDetailData {
    fun offering(expiresAtMillis: Long): DetailOffering =
        DetailOffering(
            event =
                EventDetail(
                    id = "samudra",
                    title = "Samudra",
                    subtitle = "Tur Gelombang 2026",
                    meta = "Sab, 12 Des 2026 · 19.00 WIB · Stadion Utama GBK, Jakarta",
                ),
            categories =
                listOf(
                    SeatCategory("fest-a", "Festival A", "Berdiri · paling dekat panggung", 1_250_000, stock = 0),
                    SeatCategory("fest-b", "Festival B", "Berdiri · di belakang Festival A", 850_000, stock = 120),
                    SeatCategory(
                        "tribun-bawah",
                        "Tribun Bawah",
                        "Duduk · bebas pilih kursi di tribun bawah",
                        650_000,
                        stock = 4,
                    ),
                    SeatCategory(
                        "tribun-atas",
                        "Tribun Atas",
                        "Duduk · bebas pilih kursi di tribun atas",
                        450_000,
                        stock = 300,
                    ),
                ),
            profile = AccountProfile(name = "Nadia Saputri", email = "nadia.saputri@gmail.com"),
            adminFeeRupiah = 10_000,
            reservedUnits = 3,
            expiresAtMillis = expiresAtMillis,
        )
}

/**
 * Stub [DetailRepository]: placeholder content, every NIK accepted, fake booking id.
 *
 * shortcut: TODO(FR-15-M1 #80) NIK validation API, TODO(FR-16-M1 #81) booking + stock hold,
 * TODO(FR-17-M1 #82) server-driven expiresAt. The window here starts when the detail is loaded.
 */
class StubDetailRepository(
    private val clock: ServerClock,
) : DetailRepository {
    override suspend fun getDetail(eventId: String): DetailOffering =
        StubDetailData.offering(expiresAtMillis = clock.nowMillis() + ORDER_WINDOW_MILLIS)

    override suspend fun isNikAccepted(nik: String): Boolean = true

    override suspend fun createBooking(request: BookingRequest): Booking = Booking(bookingId = "stub-booking")
}
