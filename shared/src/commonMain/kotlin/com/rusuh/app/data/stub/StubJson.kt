package com.rusuh.app.data.stub

/**
 * One JSON payload per endpoint, copied from the `example`s of the OpenAPI contract (synthetic data only: masked
 * NIKs, example.com emails). Time-dependent members (`serverNow`, `expiresAt`, `turnExpiresAt`, ...) are
 * placeholders resolved by [StubClock.render], so a countdown is relative to the injected clock. When the
 * contract changes, update the example here; `StubJsonTest` decodes each one and catches the drift.
 */
object StubJson {
    const val EVENT_GBK =
        """
        {"eventId":"evt_2026_gbk","name":"Samudra","subtitle":"Tur Gelombang 2026","format":"Tour",
         "venue":"Stadion Utama GBK","venueShort":"GBK","city":"Jakarta","timezone":"Asia/Jakarta",
         "startsAt":"2026-12-12T12:00:00Z","doorsOpenAt":"2026-12-12T10:00:00Z",
         "saleOpensAt":"2026-11-14T03:00:00Z","waitingRoomOpensAt":"2026-11-14T02:30:00Z",
         "saleEndsAt":"2026-11-20T16:59:59Z","state":"on_sale",
         "priceFrom":{"amount":750000,"currency":"IDR"},"capacity":12000,"soldOutAt":null,
         "imageUrl":"https://cdn.rusuh.id/events/evt_2026_gbk/hero.jpg"}
        """

    const val EVENT_JIS =
        """
        {"eventId":"evt_2026_jis","name":"Lentera Senja","subtitle":"Konser Satu Malam","format":"Concert",
         "venue":"Jakarta International Stadium","venueShort":"JIS","city":"Jakarta","timezone":"Asia/Jakarta",
         "startsAt":"2027-01-23T12:30:00Z","doorsOpenAt":"2027-01-23T10:30:00Z",
         "saleOpensAt":"2026-12-01T03:00:00Z","waitingRoomOpensAt":"2026-12-01T02:30:00Z",
         "saleEndsAt":"2027-01-10T16:59:59Z","state":"coming_soon",
         "priceFrom":{"amount":500000,"currency":"IDR"},"capacity":30000,"soldOutAt":null,
         "imageUrl":"https://cdn.rusuh.id/events/evt_2026_jis/hero.jpg"}
        """

    /** `GET /core/events`. */
    const val EVENTS = """{"items":[$EVENT_GBK,$EVENT_JIS],"serverNow":"@{now}"}"""

    /** `GET /events/{eventId}`. */
    const val EVENT_DETAIL = EVENT_GBK

    /** `GET /events/{eventId}/categories`. */
    const val CATEGORIES =
        """
        {"eventId":"evt_2026_gbk","categories":[
          {"categoryId":"cat_vip","name":"VIP","seating":"seated","entryGate":"Gerbang 1",
           "price":{"amount":3500000,"currency":"IDR"},"availability":"almost_gone","available":12},
          {"categoryId":"cat_festival_b","name":"Festival B","seating":"standing","entryGate":"Gerbang 5",
           "price":{"amount":1500000,"currency":"IDR"},"availability":"available","available":840},
          {"categoryId":"cat_tribune","name":"Tribun","seating":"seated","entryGate":"Gerbang 9",
           "price":{"amount":750000,"currency":"IDR"},"availability":"sold_out","available":0}],
         "maxTicketsPerOrder":3,"maxTickets":3,"adminFee":{"amount":15000,"currency":"IDR"},
         "expiresAt":"@{now+300}","serverNow":"@{now}"}
        """

    /** `POST /events/{eventId}/attendees/validate`. */
    const val ATTENDEE_VALIDATION =
        """{"validationToken":"av_01JB2Y6M1F.3174-0004","nikMasked":"************0004","expiresAt":"@{now+300}"}"""

    private const val PRICE =
        """
        {"lines":[{"categoryId":"cat_festival_b","name":"Festival B","quantity":2,
           "unitPrice":{"amount":1500000,"currency":"IDR"},"lineTotal":{"amount":3000000,"currency":"IDR"}}],
         "ticketSubtotal":{"amount":3000000,"currency":"IDR"},"adminFee":{"amount":15000,"currency":"IDR"},
         "total":{"amount":3015000,"currency":"IDR"}}
        """

    private const val ATTENDEES =
        """
        [{"categoryId":"cat_festival_b","categoryName":"Festival B","fullName":"Rina Wulandari",
          "nikMasked":"3174 •••• •••• 0004"},
         {"categoryId":"cat_festival_b","categoryName":"Festival B","fullName":"Dimas Pratama",
          "nikMasked":"3275 •••• •••• 0019"}]
        """

    /** A held booking, `POST /bookings` (201) and an item of `GET /bookings/active`. */
    const val BOOKING_HELD =
        """
        {"bookingId":"bk_01JB2Y6Z8Q","eventId":"evt_2026_gbk","status":"SELECTION","price":$PRICE,
         "orderCode":"RSH-26-084731","attendees":$ATTENDEES,"expiresAt":"@{now+300}","refundRequired":false,
         "tickets":[],"paidAt":null,"paymentMethod":null,"createdAt":"@{now}","serverNow":"@{now}"}
        """

    /** `GET /bookings/active`. */
    const val BOOKINGS_ACTIVE = """{"items":[$BOOKING_HELD]}"""

    const val TICKET_1 =
        """
        {"ticketId":"tk_01JB2YBA1","ticketCode":"SMD-FB-04827","bookingId":"bk_01JB2Y6Z8Q","eventId":"evt_2026_gbk",
         "categoryId":"cat_festival_b","categoryName":"Festival B","seating":"standing","entryGate":"Gerbang 5",
         "holderName":"Rina Wulandari","nikMasked":"3174 •••• •••• 0004",
         "qrPayload":"RSH1.SMD-FB-04827.Q2hlY2tJbi1zaWduZWQ","issuedAt":"@{now-60}"}
        """

    const val TICKET_2 =
        """
        {"ticketId":"tk_01JB2YBA2","ticketCode":"SMD-FB-04828","bookingId":"bk_01JB2Y6Z8Q","eventId":"evt_2026_gbk",
         "categoryId":"cat_festival_b","categoryName":"Festival B","seating":"standing","entryGate":"Gerbang 5",
         "holderName":"Dimas Pratama","nikMasked":"3275 •••• •••• 0019",
         "qrPayload":"RSH1.SMD-FB-04828.Q2hlY2tJbi1zaWduZWQ","issuedAt":"@{now-60}"}
        """

    /** A paid booking with its tickets, `GET /bookings/{bookingId}`. */
    const val BOOKING_PAID =
        """
        {"bookingId":"bk_01JB2Y6Z8Q","eventId":"evt_2026_gbk","status":"PAID","price":$PRICE,
         "orderCode":"RSH-26-084731","attendees":$ATTENDEES,"expiresAt":null,"refundRequired":false,
         "tickets":[$TICKET_1,$TICKET_2],"paidAt":"@{now-60}","paymentMethod":"qris","createdAt":"@{now-360}",
         "serverNow":"@{now}"}
        """

    /** `GET /tickets`. */
    const val TICKETS = """{"items":[$TICKET_1,$TICKET_2]}"""

    /** `GET /tickets/{ticketId}`. */
    const val TICKET_DETAIL = TICKET_1

    private const val QUEUE_TOKEN =
        "eyJhbGciOiJFZERTQSIsImtpZCI6InEtMjAyNi0xMSJ9.eyJldnQiOiJldnRfMjAyNl9nYmsifQ.c2lnbmF0dXJl"

    /** `POST /queue/{eventId}/join`: a place behind the frontier. */
    const val QUEUE_JOIN =
        """
        {"eventId":"evt_2026_gbk","state":"open","seq":41260,"served":40012,"opensAt":"2026-11-14T03:00:00Z",
         "turnExpiresAt":null,"serverNow":"@{now}","queueToken":"$QUEUE_TOKEN","accountAttached":true}
        """

    /** `GET /queue/{eventId}/me`: the turn has come (`served` passed `seq`). */
    const val QUEUE_ME =
        """
        {"eventId":"evt_2026_gbk","state":"open","seq":41260,"served":41262,"opensAt":"2026-11-14T03:00:00Z",
         "turnExpiresAt":"@{now+60}","serverNow":"@{now}","queueToken":"$QUEUE_TOKEN","accountAttached":true}
        """

    /** `GET /queue/{eventId}/frontier` (`opensAt` and `ts` are epoch seconds). */
    const val QUEUE_FRONTIER =
        """
        {"event":"evt_2026_gbk","state":"open","served":41250,"sellable":3120,"reserved":1800,"held":640,
         "ratePerMin":450,"rateLow":380,"rateHigh":540,"rateSource":"measured","streamFrom":2000,
         "opensAt":@{epoch-27600},"ts":@{epoch}}
        """

    /** `POST /queue/{eventId}/claim`. */
    const val QUEUE_CLAIM =
        """
        {"admissionToken":"adm_01JB2Y5W3N8Qm4sT7vY2xK9pL","slotId":"s_01JB2Y5W3N","maxTickets":3,
         "expiresAt":"@{now+900}","serverNow":"@{now}"}
        """

    /** `POST /auth/otp`. */
    const val AUTH_OTP = """{"challengeId":"ch_01JB2X9R2T","expiresAt":"@{now+600}","resendAfterSeconds":60}"""

    private const val USER =
        """{"userId":"usr_01JB2X4K7M","email":"rina.wulandari@example.com","fullName":"Rina Wulandari","locale":"id"}"""

    /** `POST /auth/otp/verify`. */
    const val AUTH_VERIFY =
        """
        {"accessToken":"eyJhbGciOiJFZERTQSIsImtpZCI6InEtMjAyNi0xMSJ9.eyJzdWIiOiJ1c3JfMDFKQjJYNEs3TSJ9.c2lnbmF0dXJl",
         "expiresIn":900,"refreshToken":"rt_7Qm2v9XcLp4sN8dK1aZ0","isNewAccount":false,"user":$USER}
        """

    /** `POST /auth/refresh` (a rotated refresh token). */
    const val AUTH_REFRESH =
        """
        {"accessToken":"eyJhbGciOiJFZERTQSIsImtpZCI6InEtMjAyNi0xMSJ9.eyJzdWIiOiJ1c3JfMDFKQjJYNEs3TSJ9.c2lnbmF0dXJl",
         "expiresIn":900,"refreshToken":"rt_Vb3n8QwE1zKp5Ls2Hy7c","isNewAccount":false,"user":$USER}
        """

    /** `GET /me`. */
    const val ME = USER

    /** `POST /devices` (a 30-day device token). */
    const val DEVICE =
        """
        {"deviceToken":"eyJhbGciOiJFZERTQSIsImtpZCI6InEtMjAyNi0xMSJ9.eyJldnQiOiJldnRfMjAyNl9nYmsifQ.c2lnbmF0dXJl",
         "expiresAt":"@{now+2592000}"}
        """

    /** `POST /bookings/{bookingId}/cancel`. */
    const val BOOKING_CANCELLED =
        """
        {"bookingId":"bk_01JB2Y6Z8Q","eventId":"evt_2026_gbk","status":"CANCELLED","price":$PRICE,
         "orderCode":"RSH-26-084731","attendees":$ATTENDEES,"expiresAt":null,"refundRequired":false,
         "tickets":[],"paidAt":null,"paymentMethod":null,"createdAt":"@{now-120}","serverNow":"@{now}"}
        """

    /** `POST /bookings/{bookingId}/payment`. */
    const val BOOKING_PAYMENT =
        """
        {"bookingId":"bk_01JB2Y6Z8Q","snapToken":"66e4fa55-fdac-4ef9-91b5-733b97d1b862",
         "paymentExpiresAt":"@{now+600}",
         "redirectUrl":"https://app.sandbox.midtrans.com/snap/v4/redirection/66e4fa55-fdac-4ef9-91b5-733b97d1b862",
         "serverNow":"@{now}"}
        """

    /** `POST /bookings/{bookingId}/check-status`: the contract only references the `Booking` schema (no example). */
    const val CHECK_STATUS = BOOKING_PAID
}
