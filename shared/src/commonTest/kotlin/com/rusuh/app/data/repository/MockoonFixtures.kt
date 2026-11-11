package com.rusuh.app.data.repository

// Response bodies copied from the local Mockoon mock (workspace-local/mockoon, build_mock_env.py) with the
// templated times frozen. The server "now" of the fixtures is 2026-11-11T17:37:29+07:00 (= 10:37:29Z).

/** The server now of the fixtures in epoch millis: 2026-11-11T10:37:29Z. */
const val FIXTURE_NOW_MILLIS = 1_794_393_449_000L

private fun event(
    id: String,
    name: String,
    subtitle: String,
    state: String,
    price: Int,
): String =
    """{"eventId": "$id", "name": "$name", "subtitle": "$subtitle", "format": "Tour", """ +
        """"venue": "Stadion Utama GBK", """ +
        """"venueShort": "GBK", "city": "Jakarta", "timezone": "Asia/Jakarta", """ +
        """"startsAt": "2026-12-12T19:00:00+07:00", "doorsOpenAt": "2026-12-12T16:00:00+07:00", "state": "$state", """ +
        """"priceFrom": {"amount": $price, "currency": "IDR"}, "capacity": 12000}"""

/** [this] event with extra members (`, "key": value`) appended. */
private fun String.withMembers(extra: String): String = removeSuffix("}") + extra + "}"

private const val COMING_OPENS = """, "saleOpensAt": "2026-12-01T10:00:00+07:00", """
private const val COMING_ROOM = """"waitingRoomOpensAt": "2026-12-01T09:30:00+07:00""""
private const val WR_OPENS = """, "saleOpensAt": "2026-11-11T17:38:59+07:00", """
private const val WR_ROOM = """"waitingRoomOpensAt": "2026-11-11T17:36:29+07:00""""
private const val SOLD_OUT_AT = """, "soldOutAt": "2026-11-09T12:41:00+07:00""""
private const val SALE_ENDS_AT = """, "saleEndsAt": "2026-10-30T23:00:00+07:00""""

/** The seven events of `GET /events`, one per state, as the mock serves them. */
val EVENT_FIXTURES: List<String> =
    listOf(
        event("evt_coming", "Langit Sore", "Senja Panjang Live", "coming_soon", 325000)
            .withMembers(COMING_OPENS + COMING_ROOM),
        event("evt_wr_open", "Nada Pantai", "Festival Musim Panas", "waiting_room_open", 275000)
            .withMembers(WR_OPENS + WR_ROOM),
        event("evt_samudra", "Samudra", "Tur Gelombang 2026", "on_sale", 450000),
        event("evt_almost", "Pelita", "Tur Kota Lama", "almost_gone", 350000),
        event("evt_full", "Kelana", "Tur Pulau", "temporarily_full", 400000),
        event("evt_soldout", "Rimba Raya", "Showcase", "sold_out", 300000).withMembers(SOLD_OUT_AT),
        event("evt_ended", "Bayu Fest", "Edisi Perdana", "ended", 250000).withMembers(SALE_ENDS_AT),
    )

/** `GET /events` with all seven events. */
val EVENTS_JSON: String =
    """{"items": [${EVENT_FIXTURES.joinToString(",")}], "serverNow": "2026-11-11T17:37:29+07:00"}"""

const val DEVICE_JSON = """{"deviceToken": "mock-device-token", "expiresAt": "2030-01-01T00:00:00Z"}"""

/** `POST /queue/{evt}/join` and `GET /me`: number 400, served 100. */
fun placeJson(
    eventId: String = "evt_1",
    state: String = "open",
    seq: Int? = 400,
    served: Int = 100,
    extra: String = "",
): String {
    val numbers = if (seq == null) "" else """, "seq": $seq, "served": $served"""
    return """{"eventId": "$eventId", "state": "$state", "queueToken": "mock-queue-token-$eventId", """ +
        """"serverNow": "2026-11-11T17:37:29+07:00", "accountAttached": false$numbers$extra}"""
}

/** `GET /queue/{evt}/frontier` at the fixture time. [opensAt] is Unix seconds. */
fun frontierJson(
    served: Int,
    state: String = "open",
    reason: String? = null,
    rate: Int = 300,
    opensAt: Long? = null,
): String {
    val reasonPart = reason?.let { """, "reason": "$it"""" }.orEmpty()
    val opensPart = opensAt?.let { """, "opensAt": $it""" }.orEmpty()
    return """{"event": "evt_1", "state": "$state", "served": $served, "sellable": 3120, "reserved": 1800, """ +
        """"held": 640, "ratePerMin": $rate, "rateLow": ${rate * 4 / 5}, "rateHigh": ${rate * 6 / 5}, """ +
        """"rateSource": "observed", "streamFrom": 2000, "ts": ${FIXTURE_NOW_MILLIS / 1_000L}$reasonPart$opensPart}"""
}

const val ADMISSION_JSON =
    """{"admissionToken": "mock-admission-token", "slotId": "s_mock_01", "maxTickets": 3, """ +
        """"expiresAt": "2026-11-11T17:42:29+07:00", "serverNow": "2026-11-11T17:37:29+07:00"}"""

fun problemJson(
    status: Int,
    code: String,
): String =
    """{"type": "https://api.rusuh.id/problems/x", "title": "t", "status": $status, "code": "$code", "detail": "d"}"""
