# 02 · API and flows (mobile view)

Contract: `rusuh-documentation/api/openapi.yaml` **0.3.0-draft** (copy in `workspace-local/Rusuh Booking Tiket API.openapi.json`
— may differ; compare before coding). Also `api/tokens.md`, `api/shared-keys.md`, `api/events/booking.v1.schema.json`,
`analysis/hifi-api-review.md`. It is a **draft**; GitHub M4 issues for Payment / My Queues are **parked until the
contract is frozen** (standing team rule).

## Conventions

JSON/camelCase, RFC 3339 UTC times, money = `{amount:int rupiah, currency:"IDR"}`. Every countdown payload carries
`expiresAt` + `serverNow` → count down with the difference. Errors: RFC 9457 `application/problem+json` with stable
`code` (translate client-side). `Accept-Language: id|en`. Creating/charging writes need `Idempotency-Key` (UUID).

## Tokens / headers

| Token | Header | From | Life |
| --- | --- | --- | --- |
| Device | `X-Device-Token` | `POST /devices` (mobile sends `platform`, `installId`, `attestation` = Play Integrity/App Attest) | 30 d |
| Queue | `X-Queue-Token` | `POST /queue/{evt}/join`, `GET …/me` | until event closes |
| Access | `Authorization: Bearer` | `POST /auth/otp/verify` | 15 min |
| Refresh | body (mobile) | verify / refresh | 30 d rotating |
| Admission | `X-Admission-Token` | `POST /queue/{evt}/claim` | 5 min |
| Attendee validation | in booking body | `POST /events/{evt}/attendees/validate` | until order window ends |
| Payment handoff | URL fragment `#code=` | `POST /payment-sessions` (needs `X-App-Attestation`) | 60 s, single use |

Device ids: `m_…` on mobile (derived from install id, survives restart). Turnstile token only for web.

## Endpoints the mobile app uses

| Area | operationId | Call | Notes |
| --- | --- | --- | --- |
| Home | `listEvents` | `GET /events?state=…` | CDN cached 5–10 s; items + `serverNow`. `EventSummary`: eventId, name, subtitle, format, venue, venueShort, city, timezone, startsAt, doorsOpenAt, saleOpensAt, waitingRoomOpensAt, saleEndsAt, state, priceFrom, capacity, soldOutAt, imageUrl |
| Home | `getEvent` | `GET /events/{id}` | |
| Device | `registerDevice` | `POST /devices` | rate-limited; 503 + Retry-After if attestation down |
| Queue | `joinQueue` | `POST /queue/{evt}/join` | idempotent per device; returns `QueuePlace` (state, seq?, served, opensAt, turnExpiresAt, queueToken, accountAttached). 409 account has place elsewhere, 410 closed. Call again after login to attach account |
| Queue | `getMyPlace` | `GET /queue/{evt}/me` | after the pre-sale draw (0–30 s jitter) and when `served ≥ seq` to get `turnExpiresAt` |
| Queue | `leaveQueue` | `DELETE /queue/{evt}/me` | 204, idempotent ("Keluar dari antrian") |
| Queue | `getFrontier` | `GET q.rusuh.id/v1/queue/{evt}/frontier` | cached 1 s; no auth. `Frontier`: state, served, sellable, reserved, held, ratePerMin, streamFrom, reason, opensAt (unix s), ts (unix s) |
| Queue | `streamQueue` | `GET q.rusuh.id/v1/queue/{evt}/stream` (SSE) | only when `seq − served ≤ streamFrom`; events `frontier`, `turn`; `: hb` every 15 s; 409 TOO_FAR, 503 STREAM_FULL; fallback to 2 s polling on 2 failed connects / no first frontier in 5 s / 30 s silence; retry stream after 60 s; stream ≤10 min ±20 % |
| Queue | `claimTurn` | `POST /queue/{evt}/claim` | when `seq ≤ served`; no session needed; returns `Admission` (admissionToken, slotId, reservedUnits, loginRequired, expiresAt, serverNow). 409 NOT_YET/FULL/NO_STOCK, 410 MISSED/EXPIRED/QUEUE_CLOSED. Safe to retry |
| Auth | `requestOtp` | `POST /auth/otp` | email, consentVersion, locale, fullName (only if new); 202 with `challengeId`, `resendAfterSeconds`; gated by queue/admission token or Turnstile |
| Auth | `verifyOtp` | `POST /auth/otp/verify` | `{challengeId, code}` → `Session` (accessToken, expiresIn, refreshToken, isNewAccount, user). 400 WRONG_CODE (`attemptsLeft`), 410 CHALLENGE_EXPIRED/LOCKED |
| Auth | `refreshSession`, `logout`, `getMe` | `/auth/refresh`, `/auth/logout`, `/me` | |
| Account | `startEmailChange`, `verifyEmailChange` | `/me/email-change[/verify]` | two codes (old+new email) |
| Detail | `getCategories` | `GET /events/{evt}/categories` | needs Bearer + admission token; returns categories, maxTicketsPerOrder (3), reservedUnits, adminFee (per order), expiresAt, serverNow. `Category`: categoryId, name, description, seating, entryGate, price, availability enum (available/almost_gone/sold_out), optional count |
| Detail | `validateAttendee` | `POST /events/{evt}/attendees/validate` | `{fieldIndex 0-2, fullName, nik}` → `{validationToken, nikMasked, expiresAt}`; 409 NIK_SEAT_TAKEN/DUPLICATE_NIK, 422 NIK_INVALID, 429, 503 DUKCAPIL_UNAVAILABLE |
| Booking | `createBooking` | `POST /bookings` (Idempotency-Key) | `{eventId, tickets:[{categoryId, validationToken}]}` (1–3); → `Booking` HELD with `expiresAt` (+10 min). 409 codes: NIK_SEAT_TAKEN, DUPLICATE_NIK, ATTENDEE_NOT_VALIDATED, QUANTITY_OVER_RESERVATION, ACCOUNT_HAS_ACTIVE_BOOKING, CATEGORY_SOLD_OUT |
| Booking | `getActiveBooking`, `getBooking`, `cancelBooking` | `/bookings/active`, `/bookings/{id}`, `/bookings/{id}/cancel` | active = resume after app closed; getBooking = source of truth for result screens |
| Tickets | `listMyTickets`, `getTicket`, `downloadBookingTickets` | `/tickets`, `/tickets/{id}`, `/bookings/{id}/tickets.pdf` | Ticket: ticketCode, categoryName, seating, entryGate, holderName, nikMasked, qrPayload. PDF only when PAID (409 BOOKING_NOT_PAID) |
| Payment | `createPaymentSession` | `POST /payment-sessions` | needs Bearer + device token + `X-App-Attestation`; → `{handoffCode, expiresAt, payUrl}` |
| Payment | (web page side) | `exchangeHandoffCode`, `getPaymentPage`, `startSnapPayment` (optional `method`), `getPaymentStatus` (poll 3–5 s; `returnUrl` App Link when final) | These live on `pay.rusuh.id`; the **payment page is web**, the app just opens it and handles the return |

`PaymentMethod` enum (for the method list screen): qris, gopay, shopeepay, bca_va, mandiri_va, bni_va, bri_va,
permata_va, cimb_va, card.

Error codes: VALIDATION_FAILED, INTERNAL_ERROR, VERIFICATION_UNAVAILABLE, UNAUTHORIZED, ADMISSION_INVALID, NOT_FOUND,
RATE_LIMITED, QUEUE_CLOSED, NOT_YET, FULL, MISSED, EXPIRED, NO_STOCK, TOO_FAR, STREAM_FULL,
ACCOUNT_HAS_PLACE_ELSEWHERE, WRONG_CODE, CHALLENGE_EXPIRED, CHALLENGE_LOCKED, NIK_INVALID, NIK_SEAT_TAKEN,
DUPLICATE_NIK, ATTENDEE_NOT_VALIDATED, QUANTITY_OVER_RESERVATION, DUKCAPIL_UNAVAILABLE, ACCOUNT_HAS_ACTIVE_BOOKING,
CATEGORY_SOLD_OUT, BOOKING_NOT_HELD, BOOKING_NOT_PAID, NOT_CANCELLABLE, CODE_INVALID, PAYMENT_PROVIDER_BUSY.

## Mobile flow, step by step

1. **Home**: `listEvents`; tap event → by state: `coming_soon` countdown only; `waiting_room_open`/`on_sale`/`almost_gone`
   → queue; `sold_out`/`ended` disabled.
2. **Device**: register once (`registerDevice` with install id + attestation); keep token (30 d).
3. **Join** (`joinQueue`) → `preopen` (countdown from `opensAt`, "Mengundi urutan…", then `getMyPlace` with 0–30 s jitter)
   or `open` with `seq`.
4. **Wait**: poll frontier (or SSE near front). Show "Anda antrian ke-N", "± n menit", "x orang masuk/menit",
   "Diperbarui n detik lalu", progress track (served at join → now → my seq). `paused` + reason: `stock`
   ("Penuh sementara" tickets being paid, keep waiting) vs `operator` ("Penyelenggara sedang memeriksa sistem");
   `closed` + `sold_out` → queue dissolved, drop token.
   Guest: banner to log in; logging in = `requestOtp` → `verifyOtp` → `joinQueue` again to attach account.
5. **Your turn**: when `served ≥ seq` call `claimTurn` at once. If returned 409 FULL retry 1–2 s jitter; NO_STOCK →
   back to waiting/paused; MISSED/EXPIRED → "Giliran terlewat" + "Antre lagi" (new `joinQueue`).
6. **Detail** (5-min countdown from `expiresAt − serverNow`): if `loginRequired` show login sheet first. Steppers
   capped at `min(3, reservedUnits)`. Per ticket: "Saya sendiri"/"Orang lain", name (as on KTP) + NIK →
   `validateAttendee` per field (3/min). Show tickets + "Biaya layanan" (once per order) = total. "Lanjut bayar" →
   `createBooking`.
7. **Payment** (10-min countdown): `createPaymentSession` → open `payUrl#code=…` in system tab; page does Snap. App
   handles App Link `https://app.rusuh.id/payment-result?bookingId=…`, then **`getBooking`** (never trust the link).
   On relaunch use `getActiveBooking` ("Lanjutkan pembayaran").
8. **Result**: PAID → e-ticket (`getTicket`, QR, ticketCode, PDF, add to calendar built client-side from `startsAt`);
   EXPIRED → "Pesanan dibatalkan", refund copy per ADR-0009 (flagged, not automatic); queue again.
9. **My tickets / Account**: `listMyTickets`, `getMe`, change email, language toggle ID/EN, logout.

Client rules: never use device clock for countdowns; polling is the fallback for SSE; keep one queue token per event
on the device (Home "in queue" strip and Antrian tab read each event's frontier); resume polling when app returns to
foreground; background push for "your turn" is **not** decided (FR-27).
