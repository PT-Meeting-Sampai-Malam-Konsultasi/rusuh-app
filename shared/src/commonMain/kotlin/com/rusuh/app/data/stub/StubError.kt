package com.rusuh.app.data.stub

import io.ktor.http.HttpHeaders

/**
 * Named error responses for scenario tests: script one on a route (`StubEngine.script`) to drive the failure
 * path. Bodies follow the spec's problem examples; [RateLimited] has no spec example, so it is synthetic.
 */
enum class StubError(
    val response: StubResponse,
) {
    NotFound(problem(404, "Tidak ditemukan", "NOT_FOUND", "No such resource for this caller.")),
    NikSeatTaken(
        problem(
            409,
            "NIK sudah terdaftar untuk event ini",
            "NIK_SEAT_TAKEN",
            "This NIK already holds 3 seats for the event.",
        ),
    ),
    QuantityOverReservation(
        problem(
            409,
            "Jumlah tiket melebihi jatah Anda",
            "QUANTITY_OVER_RESERVATION",
            "You asked for more tickets than your admission allows (maximum 3 per order).",
            extras = ""","categoryId":"cat_festival_b"""",
        ),
    ),
    CategorySoldOut(
        problem(
            409,
            "Kategori sudah habis",
            "CATEGORY_SOLD_OUT",
            "Pick another category.",
            extras = ""","categoryId":"cat_vip"""",
        ),
    ),
    AccountHasPlaceElsewhere(
        problem(
            409,
            "Akun sudah mengantre di perangkat lain",
            "ACCOUNT_HAS_PLACE_ELSEWHERE",
            "Leave the queue on the other device first.",
        ),
    ),
    WrongCode(
        problem(
            409,
            "Kode salah",
            "WRONG_CODE",
            "The code does not match.",
            extras = ""","attemptsLeft":3""",
        ),
    ),
    QueueExpired(
        problem(
            410,
            "Waktu Anda sudah habis",
            "EXPIRED",
            "This place was used and its time ran out; join again at the back.",
        ),
    ),
    QueueMissed(
        problem(
            410,
            "Giliran terlewat",
            "MISSED",
            "The turn passed more than 60 s ago without a claim; join again for a new number.",
        ),
    ),
    QueueClosed(
        problem(
            410,
            "Antrian sudah ditutup",
            "QUEUE_CLOSED",
            "Tickets are sold out; the queue is closed.",
        ),
    ),
    NotCancellable(
        problem(
            409,
            "Pesanan tidak bisa dibatalkan",
            "NOT_CANCELLABLE",
            "The booking is already paid, expired or has a payment pending.",
        ),
    ),
    BookingNotHeld(
        problem(
            409,
            "Pesanan tidak lagi aktif",
            "BOOKING_NOT_HELD",
            "The booking is paid, expired or cancelled.",
        ),
    ),
    PaymentProviderBusy(
        problem(
            503,
            "Layanan pembayaran sedang sibuk",
            "PAYMENT_PROVIDER_BUSY",
            "The payment provider is unavailable; the countdown keeps running.",
        ),
    ),
    NikInvalid(
        problem(
            422,
            "NIK tidak valid",
            "NIK_INVALID",
            "The NIK format is wrong, or the NIK was not found.",
        ),
    ),
    DuplicateNik(
        problem(
            409,
            "NIK yang sama dipakai lebih dari sekali",
            "DUPLICATE_NIK",
            "Each attendee in an order needs a different NIK.",
        ),
    ),
    AttendeeNotValidated(
        problem(
            409,
            "Data pemegang tiket belum divalidasi",
            "ATTENDEE_NOT_VALIDATED",
            "Validate every attendee before holding the booking.",
        ),
    ),
    AccountHasActiveBooking(
        problem(
            409,
            "Anda masih punya pesanan aktif",
            "ACCOUNT_HAS_ACTIVE_BOOKING",
            "Finish or cancel the active booking first.",
        ),
    ),
    AdmissionInvalid(
        problem(
            403,
            "Admission tidak berlaku",
            "ADMISSION_INVALID",
            "Missing, expired or not matching a live slot; back to the waiting room.",
        ),
    ),

    /** 429 with `Retry-After: 30`. */
    RateLimited(
        problem(
            429,
            "Terlalu banyak permintaan",
            "RATE_LIMITED",
            "Too many requests; retry later.",
        ).withRetryAfter(30),
    ),
    DukcapilUnavailable(
        problem(
            503,
            "Verifikasi NIK sedang tidak tersedia",
            "DUKCAPIL_UNAVAILABLE",
            "Dukcapil is unavailable; try again.",
        ),
    ),

    /** 503 with `Retry-After: 10`. */
    ServiceUnavailable(
        problem(
            503,
            "Layanan sedang tidak tersedia",
            "SERVICE_UNAVAILABLE",
            "The service is temporarily unavailable.",
        ).withRetryAfter(10),
    ),
}

private const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"

private fun problem(
    status: Int,
    title: String,
    code: String,
    detail: String,
    extras: String = "",
): StubResponse {
    val type = "https://api.rusuh.id/problems/${code.lowercase().replace('_', '-')}"
    return StubResponse.problem(
        status = status,
        body =
            """{"type":"$type","title":"$title","status":$status,"code":"$code",""" +
                """"detail":"$detail","traceId":"$TRACE_ID"$extras}""",
    )
}

private fun StubResponse.withRetryAfter(seconds: Int) =
    copy(
        headers = headers + (HttpHeaders.RetryAfter to seconds.toString()),
    )
