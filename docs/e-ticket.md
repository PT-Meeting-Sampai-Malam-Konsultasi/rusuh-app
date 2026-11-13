# E-ticket (FR-22: #71 page, #108 QR)

The e-ticket page opens from "Lihat e-tiket" on an order in the Tiket tab (`TicketsEffect.OpenETicket(orderId)`).
It is a pushed route, `Screen.ETicket` (`eticket/{orderId}`, the id URL-encoded), that renders the Tiket tab chrome
(header and bottom bar with Tiket selected). Back returns to the Tiket tab. One vertical scroll, not a pager:
paid stamp, headline, intro, calendar button, order line, one card per ticket, and the "Di pintu masuk" steps.

## Where the data comes from

| Shown | Source |
|---|---|
| Ticket code, category, seating (`standing` / `seated`), entry gate, `nikMasked`, `qrPayload` | API `Ticket` (`GET /tickets`, `GET /tickets/{ticketId}`) |
| Event name, short venue, door time, paid time, zone, order total, payment method, e-mail | **not in the API `Ticket`**; `shortcut:` taken from the order the Tiket tab already knows (stub JSON in `TicketsRepository.kt`) |

`TicketsRepository.getOrderTickets(orderId)` returns the `ETicketOrder`; `GetETicketUseCase` maps unknown orders and
orders without tickets to `NotFound`; `ETicketViewModel` (MVI) exposes Loading / Content / Error / NotFound.
Real wiring (TICKET-00-M4) is `GET /tickets` grouped by `bookingId` plus an event lookup for the order-level facts.

Door and paid times are formatted on the event's wall clock (WIB / WITA / WIT) without a date library; weekday and
month names and the time separator live in `values/`, `values-id/`, and `values-in/` string resources.

## QR code (#108)

- The QR content is exactly `qrPayload` (for example `RSH1.SMD-FB-04827.<signature>`), never the bare `ticketId`.
  It is not logged and not put in the accessibility description (TalkBack hears "QR code for ticket <code>").
- Encoder: `com.rusuh.app.qr.QrMatrix`, pure Kotlin in `commonMain` (byte mode, versions 1-40, ECC level M, mask by
  lowest penalty), a port of the Nayuki QR Code generator (MIT, header kept). The `QrCode` composable draws it on a
  Canvas with a 4 module quiet zone, black modules on a white square in every theme.
- Verified module for module against the reference encoder (`qrcodegen`) over 320 payloads, every version and ECC
  level at and just under capacity; the unit tests embed reference matrices.
- Capacity at level M is 2331 bytes; a longer payload throws `QrPayloadTooLongException` (the composable draws an
  empty square instead).

## Decisions

- No PDF anywhere: no "Unduh semua (PDF)" button, and the PDF mention is dropped from the e-mail sentence and from
  step 1 of "Di pintu masuk".
- The masked NIK from the API is shown on each ticket; the holder name is not (as in the design). The app never stores a
  full NIK.
- "Tambah ke kalender" is shown but inert (`ETicketActions.onAddToCalendar` is null by default); the Android calendar
  intent / iOS EventKit work is a separate slice.

## Open questions

- Does the backend e-mail the e-ticket? The intro sentence ("Kami juga mengirim e-tiket ke ...") assumes it does.
- Where do the order-level event facts come from (event lookup by `eventId`, or added to the booking)?
- Time zone of the paid stamp: the event's zone today; the viewer's zone may be intended.
- Header title: the design shows "Tiket Saya" next to the logo; the shared header has none yet.
