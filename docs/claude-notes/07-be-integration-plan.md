# 07 · Mobile BE integration plan (DRAFT)

Written 2026-11-10 from `Downloads/Rusuh Booking Tiket API.openapi-2.json` (v0.1.0-draft, **no `servers` entry: no base URL yet**) and `workspace-local/rusuh-mobile-task-slicing.html`. Deadline 13 Nov 2026.
Scope: the **mobile side** (the M4 slices and the data layer under them). Backend implementation is out of scope; this file lists what the app needs from it.

## 1. What changed in openapi-2 vs openapi

- 54 operations (was 35). All 19 new ones are **admin/officer** (`/admin/*`, `/officer/*`), not needed by the app, **plus** four mobile-relevant additions:
  - `POST /bookings/{id}/payment` (`enterPayment`): atomic stock hold + Snap token, moves SELECTION to PAYMENT_PENDING. This is now the step between Detail and Payment.
  - `POST /bookings/{id}/check-status` (`checkPaymentStatus`): force a re-check with Midtrans (use for the "verifying" screen's manual retry).
  - `GET /auth/profile`, `PUT /auth/profile` (alias of `/me`, name update, no NIK), `POST /auth/otp/request` (alias of `/auth/otp`). Use `/me` and `/auth/otp`.
- Changed schemas: `Booking`/`BookingStatus` (new `SELECTION`, `PAYMENT_PENDING`; `HELD` is the legacy name, keep it in the enum), `Admission` (`slotId`, `maxTickets`, `expiresAt`; 16 min TTL), `Session`, `Attendee`, `PriceBreakdown` (adminFee only), `PaymentView`, `SnapTransaction`, `ErrorCode` (38 codes).
- Timers: SELECTION 5 min, PAYMENT_PENDING 10 min, claim window 60 s. Every countdown uses `expiresAt - serverNow`, never the device clock.

## 2. Contract facts the app must honour

| Concern | Rule |
| --- | --- |
| Errors | RFC 9457 `application/problem+json` with stable `code` (`ErrorCode`). Current `ApiClient.handle` only maps the HTTP status text: **must parse `code`, `detail`, extras (`attemptsLeft`, `categoryId`)**. |
| Money | Integer rupiah. Format on the client. |
| Idempotency | `Idempotency-Key` on `POST /bookings` (and other charging writes). One UUID per user attempt, reused on retry. |
| Tokens | `X-Device-Token` (30 d, `POST /devices`), `X-Queue-Token` (per event, from join), Bearer access (15 min), refresh (30 d, rotating, body on mobile), `X-Admission-Token` (16 min, from claim). |
| Turnstile | `X-Turnstile-Token` on `POST /devices`, `join`, `auth/otp`: needs a native challenge path (open question, see section 7). |
| Attestation | `X-App-Attestation` on `/payment-sessions`; `attestation` in `POST /devices`. INFRA-M8, P2: send nothing/stub until BE says it is enforced. |
| Queue | Poll `GET /queue/{e}/frontier` (cached 1 s). SSE only when `seq - served <= streamFrom`. Fall back to 2 s polling (2 failed connects / no frontier in 5 s / 30 s silence), retry stream after 60 s. |
| Payment | Mobile opens the Midtrans Snap page in a system tab (`redirectUrl` from `enterPayment`/`/payments/{id}/snap`, or handoff via `/payment-sessions`). Return via App Link `app.rusuh.id/payment-result`, then poll `GET /bookings/{id}` / `/payments/{id}/status` every 3-5 s with jitter. |
| Tickets | `GET /tickets` returns **flat tickets** (no event name/date, no order grouping beyond `bookingId`). Event facts come from `getEvent(eventId)` / the booking. |

## 3. Architecture (shared module)

Layered per steering (`workspace-local/kmp-docs/steering`): `data/remote` (DTO + service) -> `data/repository` (impl) -> `domain` (model + repository interface + use cases) -> feature VM (M3).

1. **Network core** (extend `data/network/*`):
   - `ApiConfig(baseUrl)` per build type; fail loudly if blank. `baseUrl` is not in the spec: needs BE.
   - `ApiError`/`ProblemDetails(code, status, detail, extras)`; `NetworkResult.Error` carries it (`code` is currently an Int).
   - Ktor plugins: `Accept-Language` (INFRA-M7), `X-Device-Token` + `Authorization` injection, 401 -> single-flight `POST /auth/refresh` -> retry once, `Idempotency-Key` helper.
   - `ServerClock`: keeps `serverNow - deviceNow` offset from each response; exposes `remaining(expiresAt)`.
2. **Secure storage seam** (INFRA-M5, `expect/actual`: Keychain / Keystore): deviceId, device token, refresh token, queue tokens per event, admission token. Access token in memory only.
3. **Auth** (AUTH-M2/M3): `DeviceRepository`, `AuthRepository` (`requestOtp`, `verifyOtp`, `refresh`, `logout`, `me`), `SessionManager` (exposes `isSignedIn`; replaces `GetMyTicketsUseCase(isSignedIn = { true })`).
4. **Per-feature repositories** replace the stubs with a **mapper per DTO -> domain** and keep each stub as the fake for tests/previews (same pattern as `StubTicketsRepository`).
5. **DI**: `networkModule` (client, config, clock), `dataModule` (repos), swapped by a `useStubs` flag so the demo works before BE exists.

## 4. Work packages

Each package = one PR, UI/MVI slices already exist or are planned per `06-next-ticket-slices-draft.md`. "Needs BE" = blocked on a backend answer in section 7. Estimates are rough for one dev.

| # | Package | Endpoints | Depends on | Needs BE | Size |
| --- | --- | --- | --- | --- | --- |
| P0 | **Network core**: config, problem+json, interceptors, ServerClock, tests with Ktor `MockEngine` | all | - | base URL | M |
| P1 | **Storage + device**: secure store `expect/actual`, `POST /devices`, device-token plugin | `/devices`, `/.well-known/jwks.json` (not needed, tokens are opaque to the app) | P0 | Turnstile on mobile | M |
| P2 | **Home API** (FR-01-M4 #21): `GET /events` -> `HomeRepository` | `/events` | P0 | - | S |
| P3 | **Auth** (AUTH-00-M4): OTP request/verify, refresh, logout, `/me`, `SessionManager` | `/auth/otp`, `/auth/otp/verify`, `/auth/refresh`, `/auth/logout`, `/me` | P1 | Turnstile; queue/admission token optional on `/auth/otp` | M |
| P4 | **Queue** (FR-04-M4 #30, FR-APP-01-M4 #50): join, me, frontier polling, SSE with fallback, claim, leave, 410 MISSED/EXPIRED handling | `/queue/{e}/*` | P1, P3 (claim needs Bearer) | SSE auth header, `streamFrom` | L |
| P5 | **Detail/booking** (FR-05-M4 #38): `getEvent`, `getCategories`, `validateAttendee` per field (3 tries/min), `createBooking` with `Idempotency-Key`, `GET /bookings/active` resume | `/events/{id}`, `/events/{id}/categories`, `/events/{id}/attendees/validate`, `/bookings`, `/bookings/active`, `/bookings/{id}/cancel` | P3, P4 (admission token) | Dukcapil flow (validationToken lifetime) | L |
| P6 | **Payment handoff** (FR-06-M4 #45): `enterPayment` -> open `redirectUrl` in system tab, App Link return, `check-status`, status polling, expired/cancelled/refund note | `/bookings/{id}/payment`, `/bookings/{id}`, `/bookings/{id}/check-status`, `/payments/{id}/status`, (`/payment-sessions` if web page required) | P5 | App Link domain + association files, `payUrl` vs `redirectUrl` choice | L |
| P7 | **Tickets** (TICKET-00-M4): `listMyTickets` + `getTicket` + `getEvent` join, group by `bookingId`, PDF download | `/tickets`, `/tickets/{id}`, `/bookings/{id}/tickets.pdf` | P3 | order total / event name on `/tickets`? | M |
| P8 | **Account** (ACCOUNT-00-M4): `/me`, `PUT /auth/profile`, email change, logout | `/me`, `/auth/profile`, `/me/email-change*` | P3 | delete-account endpoint missing | S |
| P9 | **Hardening**: retry/backoff policy, offline cache for events and tickets, error copy for every `ErrorCode` (EN+ID), attestation (INFRA-M8) | - | P2-P7 | attestation enforcement | M |

## 5. Mapping of existing stubs to work

- `HomeRepository` (`StubHomeRepository` returns empty) -> P2 `EventsApi.list()` + `EventDto.toHomeEventItem()` (`startsAt`, `state`, `priceFrom`, `imageUrl`, `venueShort`).
- `TicketsRepository` (`StubTicketsRepository`, JSON order shape) -> P7. Real shape is flat: group `TicketDto` by `bookingId`, then fill event title/date/venue/total from `getBooking`/`getEvent` (extra calls, cache per event). **Raise with BE**: either add event facts + order total to `GET /tickets`, or add `GET /bookings?status=PAID`. The stub's `TicketOrder` already models the target.
- Waiting Room / My Queues state -> P4 `QueueRepository` returning `QueueState` + `seq/served/ratePerMin`, mapped to the existing UI phases (`QueueListPhase`, `QueuePhase`).
- Detail `DetailContent`/`DetailUiState` -> P5; `BookingStatus.SELECTION` timer drives the existing expired overlay.
- Payment: native screens are **dropped** (Midtrans web UI). P6 only needs the verifying/expired/failed return screens.

## 6. Suggested order for 13 Nov

1. P0, P1, P2 (unblock everything, demoable Home with real or stub data).
2. P3 (Auth) in parallel with P4 (Queue).
3. P5 then P6 (the money path). If BE is not ready, ship P5/P6 against **stubs behind `useStubs`** so the full flow demos; keep real services behind the same interfaces.
4. P7, P8, P9 as time allows.

Fallback if BE never appears by 13 Nov: one `StubBackend` (JSON strings per endpoint, taken from the spec's `examples`) wired in `dataModule`, so every screen works end to end.

## 7. Questions for backend (blockers)

1. **Base URLs** (api, queue `q.rusuh.id`, pay `pay.rusuh.id`, staging) and `servers` in the spec. Is there a staging environment before 13 Nov?
2. **Turnstile on mobile**: how does a native app get `X-Turnstile-Token` (WebView widget, or an attestation-based bypass)? Blocks device registration, join, OTP.
3. **Attestation** (`attestation`, `X-App-Attestation`): enforced for the demo, or skippable?
4. **`GET /tickets`**: needs event name/date/venue, order code and total (or a bookings list). Today the Tiket tab can't be built from it without N+1 calls.
5. **Payment return**: confirm App Link host/path (`app.rusuh.id/payment-result`) and who hosts the association files; `redirectUrl` (Snap) vs `/payment-sessions` handoff for mobile: which is canonical?
6. **SSE auth**: `GET /queue/{e}/stream` headers (queue token?) and whether Ktor SSE is fine behind the CDN.
7. **Mock data**: a seeded test event/user/NIK set (`rusuh-mock` only has the Dukcapil mock) and fixed OTP code in non-prod.
8. **Account delete** and `PATCH me` have no endpoint (ACCOUNT-00-M4 stays partial).
9. `Session.refreshToken` rotation + reuse detection: error code on reuse (force logout)?

## 8. Risks

- No base URL, no staging: P0-P9 can only be verified against `MockEngine` and stubs.
- Turnstile + attestation can block device registration entirely on a real BE.
- Flat `GET /tickets` forces extra requests and complicates the Tiket tab.
- Large surface (P4/P5/P6) for 3 days: protect the path Home -> Queue -> Detail -> Snap -> E-ticket first, cut P8/P9.
