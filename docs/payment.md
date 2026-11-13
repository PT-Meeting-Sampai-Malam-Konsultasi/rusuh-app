# Payment Integration

Payment uses the booking API and Midtrans Snap hosted page. The backend creates the booking in `SELECTION` for five
minutes; entering payment atomically holds inventory, changes it to `PAYMENT_PENDING` for ten minutes, and returns the
first Snap token. The screen treats `PAYMENT_PENDING` as an active payment booking.

## API flow

All routes use the core API host. Bearer auth is supplied by the shared auth plugin. Payment entry and status checks
also send `X-Admission-Token` from `TokenStore`.

- `GET /core/bookings/{bookingId}` loads the payment summary. Order code, price, attendees, expiry, and server time
  come from the booking. Contact email comes from `AccountEmailStore`.
- `POST /core/bookings/{bookingId}/payment` has no request body and no idempotency header. It returns `snapToken`,
  `paymentExpiresAt`, `serverNow`, and an optional `redirectUrl`. The backend operation is idempotent while pending.
- `POST /core/bookings/{bookingId}/check-status` rechecks Midtrans and returns the authoritative booking. If the
  check call fails, the repository falls back to `GET /core/bookings/{bookingId}`.

`redirectUrl` is opened in the existing in-app WebView. The app has no safe URL builder for a token-only response;
when `redirectUrl` is absent the screen shows its generic error. `Booking` does not include event name, event date,
venue, timezone, or customer email, so event metadata stays unavailable and the email is supplied locally.

## Status and countdown

Booking statuses include `SELECTION`, `PAYMENT_PENDING`, `HELD`, `PAID`, `EXPIRED`, and `CANCELLED`. Unknown wire
statuses map to `UNKNOWN` without failing decoding. `PAYMENT_PENDING` follows the existing held-payment screen path.

The countdown uses `expiresAt` and `serverNow` from the booking. Elapsed time is measured with the monotonic clock, so
remaining time is not affected by device clock changes or timezone settings. Returning from Snap refreshes the booking
and performs one check-status call; the existing poller continues until a final booking state.

## Return handling

The WebView closes on the existing app return link. URL parameters are never trusted as payment status; the app reads
the booking from the API after return. Closing Snap or returning from an external wallet triggers the existing resume
refresh. No status requests run while the app is backgrounded.

## Stub mode and verification

Stub mode uses the in-process payment repository; MockEngine fixtures cover the booking read, payment entry, and status
check with synthetic masked attendee data. Real mode keeps the payment repository on its stub until live staging deploys
the routes. The old `/core/payments/{id}`, `/snap`, and `/status` routes are not registered. Mockoon-independent API
tests cover paths, auth headers, empty request bodies, mapping, repository fallback, and ViewModel transitions.

Live staging payment behavior and actual Midtrans/WebView handoff remain unverified because payment deployment and
queue admission are not available on staging. Contract fields that are absent from `Booking` are listed above.
