# Payment Integration

Payment handoff to Midtrans Snap (FR-06-M4 #45, FR-06-M5 #98, FR-25-M1 #85, FR-21-M1 #84).

## Overview

Payment follows the Midtrans Web UI (Snap hosted page) flow:
1. User reaches the Payment screen after ticket selection
2. App shows order summary with a 10-minute countdown
3. User taps "Continue to payment" → opens Snap page in system tab
4. User completes payment on Snap, then returns via App Link / Universal Link
5. App polls status and shows the result (success / expired / failed)

The Midtrans mobile SDK is NOT used (it is being retired). The backend creates the Snap
transaction, so no Midtrans server key is needed in the app.

## API Contract

All endpoints require a Bearer token. Currently NOT deployed on live staging (404).

**Stub fallback**: Because the payment routes return 404 on the live backend, `PaymentRepository` uses
`StubPaymentRepository` in `BackendMode.Real` (listed in `REPOSITORIES_STILL_ON_STUBS` in `DataModule.kt`).
Flipping to the real repository is a one-line change: replace the `stubOrFail` binding with
`PaymentRepositoryImpl(get())` and remove `"PaymentRepository"` from `REPOSITORIES_STILL_ON_STUBS`.

### GET /core/payments/{bookingId}

Returns `PaymentView`: order summary, price breakdown, countdown, Snap client key.

```json
{
  "bookingId": "bk_01JB2Y6Z8Q",
  "orderCode": "RSH-26-084731",
  "eventName": "Samudra",
  "eventStartsAt": "2026-12-12T12:00:00Z",
  "venueShort": "GBK",
  "timezone": "Asia/Jakarta",
  "attendees": [
    { "categoryId": "cat_festival_b", "categoryName": "Festival B", "fullName": "Rina Wulandari", "nikMasked": "3174 •••• •••• 0004" }
  ],
  "customerEmail": "rina.wulandari@example.com",
  "price": {
    "lines": [{ "categoryId": "cat_festival_b", "name": "Festival B", "quantity": 2, "unitPrice": {"amount": 1500000, "currency": "IDR"}, "lineTotal": {"amount": 3000000, "currency": "IDR"} }],
    "ticketSubtotal": {"amount": 3000000, "currency": "IDR"},
    "adminFee": {"amount": 15000, "currency": "IDR"},
    "total": {"amount": 3015000, "currency": "IDR"}
  },
  "currentSnap": null,
  "expiresAt": "2026-11-14T10:50:00Z",
  "serverNow": "2026-11-14T10:40:00Z",
  "csrfToken": "...",
  "snapClientKey": "SB-Mid-client-..."
}
```

### POST /core/payments/{bookingId}/snap

Creates (or returns) the Midtrans Snap transaction. Requires `Idempotency-Key` header.

Request body (optional):
```json
{ "method": "qris" }  // pre-select payment method, or null to let user pick
```

Response (201):
```json
{
  "attemptId": 1,
  "orderId": "bk_01JB2Y6Z8Q-1",
  "method": null,
  "snapToken": "66e4fa55-fdac-4ef9-91b5-733b97d1b862",
  "redirectUrl": "https://app.sandbox.midtrans.com/snap/v4/redirection/66e4fa55-fdac-4ef9-91b5-733b97d1b862",
  "gatewayExpiresAt": "2026-11-14T10:50:00Z"
}
```

Errors:
- 409 `BOOKING_NOT_HELD` — booking expired or not in PAYMENT_PENDING state
- 503 `PAYMENT_PROVIDER_BUSY` — Midtrans unavailable, retry with same idempotency key

### GET /core/payments/{bookingId}/status

Poll every 3-5 seconds with jitter. Stops when status is final.

Response:
```json
{
  "bookingStatus": "PAID",        // SELECTION, PAYMENT_PENDING, PAID, EXPIRED, CANCELLED
  "paymentStatus": "settled",     // none, pending, settled, denied, expired, cancelled, failed, paid_after_expiry
  "returnUrl": "https://app.rusuh.id/payment-result?bookingId=bk_01JB2Y6Z8Q",
  "serverNow": "2026-11-14T10:45:00Z"
}
```

## Return Handling (Deep Links)

### App Link / Universal Link (Production)

Domain: `https://app.rusuh.id`
Path: `/payment-result?bookingId={bookingId}`

The backend must include the app domain in `callbacks.finish` when creating the Snap
transaction. For GoPay, the callback URL is also set there.

Domain files required for production:
- Android: `assetlinks.json` at `https://app.rusuh.id/.well-known/assetlinks.json`
- iOS: `apple-app-site-association` at `https://app.rusuh.id/.well-known/apple-app-site-association`

### Custom Scheme (Local / Debug)

For local builds where the domain files don't exist:
- Scheme: `rusuh://payment-result?bookingId={bookingId}`

Declared in:
- Android: `AndroidManifest.xml` intent-filter on MainActivity
- iOS: `Info.plist` CFBundleURLTypes

### Navigation

The `PaymentResult` route:
1. NEVER trusts the URL parameters (could be spoofed)
2. Extracts only the `bookingId`
3. Calls `GET /payments/{bookingId}/status` to get actual status
4. Shows the appropriate result screen

Malformed or foreign URLs (wrong scheme/host, missing bookingId) are ignored.

## Screen States

| State | Description | User Action |
|-------|-------------|-------------|
| Loading | Fetching payment summary | — |
| Ready | Shows order summary + countdown | Tap "Continue to payment" |
| Verifying | Polling after return from Snap | Wait |
| Success | PAID: tickets issued | Go to Tiket tab |
| Expired | Booking not paid in time | Queue again / Go home |
| Error | Network or load failure | Retry |

### Expired with Refund Notice (ADR-0009)

If `paymentStatus` is `paid_after_expiry`, the payment arrived after the 10-minute window.
The booking is still cancelled, but a refund notice is shown with the customer email.

## Countdown

The countdown is driven by server time, not the device clock:
1. `expiresAt` and `serverNow` come from the API
2. Elapsed time is computed from the monotonic clock since receipt
3. Remaining = `expiresAt - (serverNow + elapsed)`

This prevents timezone issues and device clock manipulation.

## What is Verified vs Not

### Verified (Unit Tests)
- DTO parsing from contract examples
- Mapper correctness (DTO → domain)
- Repository stub behavior
- Polling logic with virtual time
- ViewModel state transitions
- Countdown expiry handling
- URL/return parsing (bookingId extraction)
- PaymentPageLauncher fake for testing

### Not Verified (Requires Backend)
- Live API calls (routes return 404)
- Actual Custom Tab / SFSafariViewController launch
- Real App Link / Universal Link handling
- Midtrans Snap page interaction
- GoPay/e-wallet deeplinks

## Local Testing with Mockoon

To test the payment flow locally:

1. Add routes to the Mockoon mock server (`workspace-local/mockoon/rusuh-mock.json`):

```json
{
  "uuid": "payment-view",
  "type": "http",
  "documentation": "GET /core/payments/{bookingId}",
  "method": "get",
  "endpoint": "v1/core/payments/:bookingId",
  "responses": [
    {
      "uuid": "payment-view-200",
      "body": "{ \"bookingId\": \"{{urlParam 'bookingId'}}\", \"orderCode\": \"RSH-26-084731\", ... }",
      "statusCode": 200
    }
  ]
}
```

2. Add the Snap creation route (`POST /core/payments/:bookingId/snap`)

3. Add the status polling route (`GET /core/payments/:bookingId/status`) — script
   it to return PENDING 2-3 times, then PAID.

4. Create a fake Snap page (simple HTML) that redirects to `rusuh://payment-result?bookingId=...`
   after a button click.

5. Run with mock mode: `./gradlew :androidApp:assembleDebug -PmockApi=true`

## Files

### Data Layer
- `data/remote/PaymentApiService.kt` — HTTP calls
- `data/remote/dto/PaymentDtos.kt` — DTOs (uses `MoneyDto` from EventDtos)
- `data/remote/mapper/PaymentMapper.kt` — DTO → domain
- `data/repository/PaymentRepositoryImpl.kt` — network implementation
- `data/repository/StubPaymentRepository.kt` — offline demo

### Domain Layer
- `domain/model/PaymentModels.kt` — domain models
- `domain/repository/PaymentRepository.kt` — interface
- `domain/usecase/PaymentUseCases.kt` — LoadPayment, StartSnapPayment, ObservePaymentStatus

### Feature Layer
- `feature/payment/PaymentContract.kt` — MVI contract
- `feature/payment/PaymentViewModel.kt` — main screen ViewModel
- `feature/payment/PaymentResultContract.kt` — result screen contract
- `feature/payment/PaymentResultViewModel.kt` — result screen ViewModel
- `feature/payment/PaymentScreen.kt` — UI composables
- `feature/payment/PaymentRoute.kt` — route wiring
- `feature/payment/PaymentPageLauncher.kt` — interface for browser launch

### Platform
- `androidMain/.../AndroidPaymentPageLauncher.kt` — Chrome Custom Tabs
- `iosMain/.../IosPaymentPageLauncher.kt` — SFSafariViewController

### Navigation
- `navigation/Screen.kt` — `Payment` and `PaymentResult` routes
- `navigation/NavGraph.kt` — route registrations

## Backend Setup Required

For the full flow to work, the backend must:

1. Deploy the payment routes (`/core/payments/...`)

2. Set `callbacks.finish` to include the app domain when creating Snap transactions:
   ```
   callbacks: {
     finish: "https://app.rusuh.id/payment-result?bookingId={orderId}"
   }
   ```

3. Host the App Link / Universal Link domain files at `https://app.rusuh.id/.well-known/`

4. For GoPay, set the mobile deeplink callback appropriately
