# 08 · API tickets (DRAFT, nothing created on GitHub)

Rewritten 2026-11-10. Target repo: `PT-Meeting-Sampai-Malam-Konsultasi/rusuh-app` (docs repo is read-only).
**Naming:** `FR-xx-Mn` where **xx is the doc FR number** and the parent is that doc FR issue (`rusuh-documentation#xx`). Data-layer base is a new `EPIC-01` with `EPIC-01-M1..M7`.
Contract: `Rusuh Booking Tiket API.openapi-2.json` (draft). Layering: `workspace-local/kmp-docs/steering` (`data-layer`, `module-structure`, `dependency-injection`).
Every FR ticket: build the real repository behind the existing UI/MVI seam, keep the stub as the fake, test with Ktor `MockEngine`; stays open until a backend exists.

**Numbering note.** Existing app tickets use the *app* parent numbers: FR-01 Home, FR-04 Waiting Room (doc FR-04..10), FR-05 Detail (doc FR-11..19), FR-06 Payment (doc FR-20..25), each with M1-M4. So for doc FR-04, FR-05 and FR-06 the new tickets start at M5 to avoid duplicate titles; for all other doc FRs they start at M1. This leaves FR-04/05/06 meaning two things in titles; flagged in section 5.

## 1. EPIC-01: Data layer setup (parent + 7 slices)

Parent title: `EPIC-01: Data Layer Foundation`
```
## Description
Service layer every FR API ticket builds on: result types, ApiService/Repository pattern from steering, environments, HTTP plugins, secure storage, device and auth session, stub switch. No FR of its own.

## Slices
- [ ] EPIC-01-M1: Data layer foundation: result types, ApiService pattern, package layout
- [ ] EPIC-01-M2: Environments and base URL config
- [ ] EPIC-01-M3: HTTP client plugins: language, tokens, refresh, idempotency, server clock
- [ ] EPIC-01-M4: Secure storage and module cache
- [ ] EPIC-01-M5: Device identity
- [ ] EPIC-01-M6: Auth session data layer (email OTP)
- [ ] EPIC-01-M7: Stub backend switch and test harness

## Out of scope
Feature repositories (FR-xx tickets), UI, backend.
```

| Ticket | Title | Depends on |
| --- | --- | --- |
| EPIC-01-M1 | Data layer foundation: result types, ApiService pattern, package layout | EPIC-00-M3 (#4) |
| EPIC-01-M2 | Environments and base URL config | M1 |
| EPIC-01-M3 | HTTP client plugins: language, tokens, refresh, idempotency, server clock | M1, M4 |
| EPIC-01-M4 | Secure storage and module cache | M1 |
| EPIC-01-M5 | Device identity | M3, M4 |
| EPIC-01-M6 | Auth session data layer (email OTP) | M3, M5 |
| EPIC-01-M7 | Stub backend switch and test harness | M1 |

### EPIC-01-M1: Data layer foundation: result types, ApiService pattern, package layout
```
## Parent
Implements: EPIC-01

## Requirements
- `ApiResult<T>` (Success(code, data) / Error(code, ProblemDetails) / Exception); replaces the string-only `NetworkResult.Error`
- `ProblemDetails(type, title, status, code: ErrorCode, detail, traceId, extras)` parsed from `application/problem+json`; `ErrorCode` enum from the spec (38 codes) with an `UNKNOWN` fallback
- `DomainResult<T>` (Success / Error(message, code) / Empty / NetworkError / TechnicalError) and `CoreUseCase<P, R>`
- ApiService helpers (status-aware get/post/put/delete) on the shared `ApiClient`; `Json { ignoreUnknownKeys = true }`
- Package layout for new code: `data/remote` (ApiService, `dto/`, `mapper/`), `data/repository`, `data/cache`, `domain/{model,repository,usecase}`, `di/`; endpoints as `const val EP_*`; DTOs `@Serializable` + `@SerialName` + defaults
- Tests with Ktor `MockEngine`: success, problem+json 4xx/409/410/422, 5xx, timeout, malformed body

## References
steering: data-layer.md, module-structure.md, dependency-injection.md; extends EPIC-00-M3 (#4)
```

### EPIC-01-M2: Environments and base URL config
```
## Parent
Implements: EPIC-01

## Requirements
- `ApiConfig(baseUrl)` per build type (debug = local mock http://10.0.2.2:3000, release = real); fail loudly when blank (the spec has no `servers` yet)
- Android debug-only cleartext config for the mock; iOS ATS exception for localhost
- Separate hosts supported: api, queue (`q.`), pay (`pay.`), configurable independently
- Koin `coreModule` provides the `HttpClient` / `ApiClient`; feature modules never build their own

## References
steering: dependency-injection.md; EPIC-00-M4 (#5)
```

### EPIC-01-M3: HTTP client plugins: language, tokens, refresh, idempotency, server clock
```
## Parent
Implements: EPIC-01

## Requirements
- `Accept-Language` from the active locale (EPIC-00-M10)
- Inject `X-Device-Token` and `Authorization: Bearer` when present; `X-Queue-Token` / `X-Admission-Token` per request via the ApiService
- 401 -> one single-flight `POST /auth/refresh`, retry once, else emit `SessionExpired`
- `Idempotency-Key` helper: one UUID per user attempt, reused on retry
- `ServerClock`: records `serverNow - deviceNow` from each response; `remaining(expiresAt)`; injectable for tests (FR-17, FR-19, FR-21)
- Tests

## References
OpenAPI info.description (Tokens and headers, conventions); ADR-0021, ADR-0025
```

### EPIC-01-M4: Secure storage and module cache
```
## Parent
Implements: EPIC-01

## Requirements
- `SecureStore` expect/actual: Android Keystore + EncryptedSharedPreferences, iOS Keychain; stores refresh token, device token, queue token per event, admission token; access token stays in memory
- `AppPreferences` (string get/set) and the `*Cache` pattern with key prefixes (`home_`, `queue_`, `book_`, `tix_`, `auth_`)
- Tests with fakes

## References
steering: data-layer.md (Module Cache); INFRA-M5 in the task list
```

### EPIC-01-M5: Device identity
```
## Parent
Implements: EPIC-01

## Requirements
- `DeviceRepository.register()` -> `POST /devices` (`platform`, `installId`, optional `attestation`); persist the token (30 d), refresh before expiry
- Attach through the M3 plugin; handle 429 / 503
- Open: `X-Turnstile-Token` on native, attestation enforcement (INFRA-M8)

## References
ADR-0003 (device-based queue)
```

### EPIC-01-M6: Auth session data layer (email OTP)
```
## Parent
Implements: EPIC-01

## Requirements
- `AuthRepository`: `requestOtp`, `verifyOtp`, `refresh`, `logout`, `me` -> `POST /auth/otp`, `/auth/otp/verify`, `/auth/refresh`, `/auth/logout`, `GET /me`
- Map WRONG_CODE (+ `attemptsLeft`), CHALLENGE_EXPIRED, CHALLENGE_LOCKED, RATE_LIMITED
- `SessionManager`: `isSignedIn` flow, rotating refresh token, reuse detection forces logout; replaces `GetMyTicketsUseCase(isSignedIn = { true })`
- No FR of its own: it unblocks FR-08 (claim needs Bearer), FR-16 and FR-22

## References
ADR-0006 (passwordless OTP)
```

### EPIC-01-M7: Stub backend switch and test harness
```
## Parent
Implements: EPIC-01

## Requirements
- `useStubs` per environment in DI: stub repositories vs real ones
- One JSON stub per endpoint from the spec examples (pattern of `StubTicketsJson`)
- Shared `MockEngine` harness; Mockoon environment kept in `workspace-local/mockoon`

## References
workspace-local/mockoon/README.md
```

## 2. FR API tickets (by doc FR)

| Ticket | Title | Endpoints | Parent (docs, read-only) |
| --- | --- | --- | --- |
| **FR-02-M1** | Event availability to active state | `GET /events` (`state`) | #2 |
| **FR-03-M1** | Sold out / unavailable to disabled state | `GET /events` (`state`) | #3 |
| **FR-04-M5** | Join queue on event click | `POST /queue/{eventId}/join`, `GET /queue/{eventId}/frontier` | #4 |
| **FR-05-M5** | Go straight to Detail when there is no queue | `GET /queue/{eventId}/frontier`, `POST /queue/{eventId}/join` | #5 |
| **FR-06-M5** | Queue position from the server | `POST /queue/{eventId}/join`, `GET /queue/{eventId}/me` | #6 |
| **FR-07-M1** | Auto-update position (polling + SSE) | `GET /queue/{eventId}/frontier`, `GET /queue/{eventId}/stream` | #7 |
| **FR-08-M1** | Claim turn and move to Detail | `POST /queue/{eventId}/claim` | #8 |
| **FR-09-M1** | Estimated wait from queue rate | `GET /queue/{eventId}/frontier` (`ratePerMin`, `rateLow`, `rateHigh`) | #9 |
| **FR-10-M1** | Keep position across refresh and relaunch | `GET /queue/{eventId}/me` | #10 |
| **FR-11-M1** | Event detail | `GET /events/{eventId}` | #11 |
| **FR-12-M1** | Categories with stock and price | `GET /events/{eventId}/categories` | #12 |
| **FR-13-M1** | Maximum 3 tickets per NIK | `POST /bookings`, `GET /events/{eventId}/categories` (`maxTickets`) | #13 |
| **FR-14-M1** | Reject quantities over stock or limit | `POST /bookings` | #14 |
| **FR-15-M1** | NIK format and duplicate validation | `POST /events/{eventId}/attendees/validate` | #15 |
| **FR-16-M1** | Stock hold on proceed to payment | `POST /bookings` (Idempotency-Key), `POST /bookings/{bookingId}/payment` | #16 |
| **FR-17-M1** | Booking countdown on Detail | `GET /bookings/{bookingId}`, categories `expiresAt` | #17 |
| **FR-18-M1** | Idle expiry releases the booking | `POST /bookings/{bookingId}/cancel`, `GET /bookings/{bookingId}` | #18 |
| **FR-19-M1** | Server-authoritative timer after refresh | `GET /bookings/active`, `GET /bookings/{bookingId}` | #19 |
| **FR-20-M1** | Payment method and Snap handoff | `POST /bookings/{bookingId}/payment`, `POST /payments/{bookingId}/snap`, `POST /payment-sessions` | #20 |
| **FR-21-M1** | Payment countdown | `GET /payments/{bookingId}`, `GET /bookings/{bookingId}` | #21 |
| **FR-22-M1** | Purchased tickets and e-ticket | `GET /tickets`, `GET /tickets/{ticketId}`, `GET /bookings/{bookingId}/tickets.pdf` | #22 |
| **FR-23-M1** | Payment expired: booking cancelled | `GET /payments/{bookingId}/status`, `GET /bookings/{bookingId}` | #23 |
| **FR-25-M1** | Payment status handling (idempotent) | `GET /payments/{bookingId}/status`, `POST /bookings/{bookingId}/check-status` | #25 |

Doc FR-24 has no ticket: No mobile work beyond consuming the REST/JSON contract; covered by EPIC-01. Traceability only.

Existing tickets these overlap with (not rewritten, see section 4): #21 FR-01-M4 (covers doc FR-01 `GET /events`), #30 FR-04-M4 (covers doc FR-06/07/09/10), #38 FR-05-M4, #45 FR-06-M4, #50 FR-APP-01-M4.

## 3. Bodies

### FR-02-M1: Event availability to active state
```
## Description
Event availability to active state (API / data layer).

## Requirements
- Map EventState on_sale / almost_gone / waiting_room_open to the active, clickable card (FR-02)
- Use `saleOpensAt` / `waitingRoomOpensAt` for the label
- Tests per state

## Technical
- Endpoints: `GET /events` (`state`)
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#2 FR-02](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/2)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-03-M1: Sold out / unavailable to disabled state
```
## Description
Sold out / unavailable to disabled state (API / data layer).

## Requirements
- Map sold_out, ended, temporarily_full to the disabled card with its status label (FR-03)
- Map `saleEndsAt` / `soldOutAt` to the label text
- Tests per state

## Technical
- Endpoints: `GET /events` (`state`)
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#3 FR-03](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/3)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-04-M5: Join queue on event click
```
## Description
Join queue on event click (API / data layer).

## Requirements
- `QueueRepository.join(eventId)` with the device token; store the queue token per event
- Queue active -> route to Waiting Room (FR-04)
- Handle 409 ACCOUNT_HAS_PLACE_ELSEWHERE (takeOver), QUEUE_CLOSED, NOT_FOUND, RATE_LIMITED
- Tests: mapping + repository

## Technical
- Endpoints: `POST /queue/{eventId}/join`, `GET /queue/{eventId}/frontier`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Note
Existing app tickets FR-04-M1..M4 use app numbering; new ones start at M5.

## Parent
[rusuh-documentation#4 FR-04](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/4)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-05-M5: Go straight to Detail when there is no queue
```
## Description
Go straight to Detail when there is no queue (API / data layer).

## Requirements
- Decide from QueueState (preopen / open / paused / closed) and the frontier whether a queue exists
- No queue -> Detail (FR-05); closed or sold out -> no navigation
- Tests for each state

## Technical
- Endpoints: `GET /queue/{eventId}/frontier`, `POST /queue/{eventId}/join`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Note
Existing app FR-05-M1..M4 are the Detail page; new ones start at M5.

## Parent
[rusuh-documentation#5 FR-05](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/5)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-06-M5: Queue position from the server
```
## Description
Queue position from the server (API / data layer).

## Requirements
- Position = `seq - served`, "Anda antrian ke-n" counted from the front (FR-06)
- Server value is the truth; no client counting
- Tests

## Technical
- Endpoints: `POST /queue/{eventId}/join`, `GET /queue/{eventId}/me`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Note
Existing app FR-06-M1..M4 are Payment; new ones start at M5.

## Parent
[rusuh-documentation#6 FR-06](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/6)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-07-M1: Auto-update position (polling + SSE)
```
## Description
Auto-update position (polling + SSE) (API / data layer).

## Requirements
- Poll the frontier: 10 s (>10k back), 5 s (<=10k), 2 s (<=1k), +-20% jitter
- SSE only when `seq - served <= streamFrom` (default 2,000); events `frontier`, `turn`; ignore `: hb`
- Fall back to 2 s polling on 2 failed connects, no first frontier in 5 s, or 30 s silence; retry the stream after 60 s; stream life 10 min +-20%
- 409 TOO_FAR / 503 STREAM_FULL -> keep polling
- Pause the stream in the background, poll once on return
- Tests (fake clock, MockEngine)

## Technical
- Endpoints: `GET /queue/{eventId}/frontier`, `GET /queue/{eventId}/stream`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Note
ADR-0015: hybrid polling + SSE, no WebSocket.

## Parent
[rusuh-documentation#7 FR-07](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/7)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-08-M1: Claim turn and move to Detail
```
## Description
Claim turn and move to Detail (API / data layer).

## Requirements
- Call claim as soon as `turn` arrives or `seq <= served` (needs Bearer: sign in first)
- Store `admissionToken`, `slotId`, `maxTickets`, `expiresAt` (60 s window)
- NOT_YET / FULL -> retry after 1 s; MISSED / EXPIRED -> Missed Turn state
- Navigate to Detail (FR-08)
- Tests

## Technical
- Endpoints: `POST /queue/{eventId}/claim`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#8 FR-08](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/8)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-09-M1: Estimated wait from queue rate
```
## Description
Estimated wait from queue rate (API / data layer).

## Requirements
- ETA = position / `ratePerMin` (mandatory per T-03); show the range when `rateLow/rateHigh` exist
- Recompute on every frontier update
- Tests

## Technical
- Endpoints: `GET /queue/{eventId}/frontier` (`ratePerMin`, `rateLow`, `rateHigh`)
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#9 FR-09](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/9)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-10-M1: Keep position across refresh and relaunch
```
## Description
Keep position across refresh and relaunch (API / data layer).

## Requirements
- Persist the queue token per event (EPIC-01-M4)
- On launch / foreground call `GET /queue/{eventId}/me` for each stored token to restore the place
- 410 MISSED / EXPIRED -> Missed Turn; 404 -> drop the token
- Tests

## Technical
- Endpoints: `GET /queue/{eventId}/me`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#10 FR-10](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/10)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-11-M1: Event detail
```
## Description
Event detail (API / data layer).

## Requirements
- Fetch name, subtitle, venue, `startsAt`, `doorsOpenAt`, image for the Detail header
- Needs the admission token to be on screen (403 ADMISSION_INVALID -> back to queue)
- Tests

## Technical
- Endpoints: `GET /events/{eventId}`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#11 FR-11](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/11)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-12-M1: Categories with stock and price
```
## Description
Categories with stock and price (API / data layer).

## Requirements
- Map each category: name, description, price, availability (TERSEDIA / HAMPIR HABIS / HABIS)
- Keep `maxTicketsPerOrder`, `maxTickets`, `adminFee`, `expiresAt`, `serverNow`
- Tests

## Technical
- Endpoints: `GET /events/{eventId}/categories`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#12 FR-12](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/12)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-13-M1: Maximum 3 tickets per NIK
```
## Description
Maximum 3 tickets per NIK (API / data layer).

## Requirements
- Enforce the cap across categories from `maxTickets` / `maxTicketsPerOrder` (FR-13)
- Map NIK_SEAT_TAKEN (NIK already holds its seats) and ACCOUNT_HAS_ACTIVE_BOOKING
- Tests

## Technical
- Endpoints: `POST /bookings`, `GET /events/{eventId}/categories` (`maxTickets`)
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#13 FR-13](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/13)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-14-M1: Reject quantities over stock or limit
```
## Description
Reject quantities over stock or limit (API / data layer).

## Requirements
- Client guard from category availability and the cap (FR-14)
- Map CATEGORY_SOLD_OUT, QUANTITY_OVER_RESERVATION back to the stepper state
- Tests

## Technical
- Endpoints: `POST /bookings`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#14 FR-14](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/14)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-15-M1: NIK format and duplicate validation
```
## Description
NIK format and duplicate validation (API / data layer).

## Requirements
- Per field: `fieldIndex`, `fullName`, `nik` (16 digits); keep the `validationToken` per field
- Map NIK_INVALID, DUPLICATE_NIK, NIK_SEAT_TAKEN, DUKCAPIL_UNAVAILABLE, RATE_LIMITED (3 attempts / min / field)
- Tests

## Technical
- Endpoints: `POST /events/{eventId}/attendees/validate`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#15 FR-15](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/15)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-16-M1: Stock hold on proceed to payment
```
## Description
Stock hold on proceed to payment (API / data layer).

## Requirements
- Create the booking, then `enterPayment` (atomic hold + Snap token, SELECTION -> PAYMENT_PENDING)
- Retry 503 Snap creation; 409 BOOKING_NOT_HELD -> error path
- Return `redirectUrl` / `snapToken` to FR-20
- Tests

## Technical
- Endpoints: `POST /bookings` (Idempotency-Key), `POST /bookings/{bookingId}/payment`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#16 FR-16](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/16)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-17-M1: Booking countdown on Detail
```
## Description
Booking countdown on Detail (API / data layer).

## Requirements
- Countdown (5 min) from `expiresAt - serverNow` via `ServerClock`
- Warning colour near the end; expiry state when it hits 0
- Tests with a fake clock

## Technical
- Endpoints: `GET /bookings/{bookingId}`, categories `expiresAt`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#17 FR-17](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/17)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-18-M1: Idle expiry releases the booking
```
## Description
Idle expiry releases the booking (API / data layer).

## Requirements
- On EXPIRED / CANCELLED leave the booking screen with a notice (FR-18)
- Explicit cancel via `POST /bookings/{id}/cancel`
- 409 NOT_CANCELLABLE handled
- Tests

## Technical
- Endpoints: `POST /bookings/{bookingId}/cancel`, `GET /bookings/{bookingId}`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#18 FR-18](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/18)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-19-M1: Server-authoritative timer after refresh
```
## Description
Server-authoritative timer after refresh (API / data layer).

## Requirements
- Never use the device clock: remaining = `expiresAt - serverNow` (+ `ServerClock` offset)
- Resume the active booking after relaunch
- Tests

## Technical
- Endpoints: `GET /bookings/active`, `GET /bookings/{bookingId}`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#19 FR-19](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/19)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-20-M1: Payment method and Snap handoff
```
## Description
Payment method and Snap handoff (API / data layer).

## Requirements
- Method choice happens on the Midtrans Snap web page (system tab); pass `method` when the app pre-selects
- Open `redirectUrl` (or `payUrl` + handoff code) in a system tab; return via App Link `app.rusuh.id/payment-result`
- Tests

## Technical
- Endpoints: `POST /bookings/{bookingId}/payment`, `POST /payments/{bookingId}/snap`, `POST /payment-sessions`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Note
Payment UI is the Midtrans web page, no native payment screens.

## Parent
[rusuh-documentation#20 FR-20](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/20)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-21-M1: Payment countdown
```
## Description
Payment countdown (API / data layer).

## Requirements
- 10-minute countdown from `expiresAt - serverNow`; it includes method choice and retries (T-07)
- On 0 move to the expired state
- Tests with a fake clock

## Technical
- Endpoints: `GET /payments/{bookingId}`, `GET /bookings/{bookingId}`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Note
The user's example: FR-21-xx refers to FR-21.

## Parent
[rusuh-documentation#21 FR-21](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/21)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-22-M1: Purchased tickets and e-ticket
```
## Description
Purchased tickets and e-ticket (API / data layer).

## Requirements
- Replace `StubTicketsRepository` behind the existing `TicketsRepository` seam; group tickets by `bookingId`
- Fill event title / date / venue / total from the booking and event (until `GET /tickets` carries them)
- PDF download
- The e-mail is sent by the backend, not the app
- Tests

## Technical
- Endpoints: `GET /tickets`, `GET /tickets/{ticketId}`, `GET /bookings/{bookingId}/tickets.pdf`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Note
Tiket tab UI already merged in #60.

## Parent
[rusuh-documentation#22 FR-22](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/22)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-23-M1: Payment expired: booking cancelled
```
## Description
Payment expired: booking cancelled (API / data layer).

## Requirements
- Map expired / cancelled to "Pesanan dibatalkan"; show the refund note when `refundRequired` (paid_after_expiry)
- Offer "Antre lagi" / "Ke beranda"
- Tests

## Technical
- Endpoints: `GET /payments/{bookingId}/status`, `GET /bookings/{bookingId}`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Parent
[rusuh-documentation#23 FR-23](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/23)

## Depends on
EPIC-01 (M1..M7 as needed)
```

### FR-25-M1: Payment status handling (idempotent)
```
## Description
Payment status handling (idempotent) (API / data layer).

## Requirements
- Poll every 3-5 s with jitter until a terminal state; manual re-check via `check-status`
- Map settled, denied, expired, cancelled, failed, paid_after_expiry; repeated terminal states are harmless
- PAID -> e-ticket (FR-22)
- Tests

## Technical
- Endpoints: `GET /payments/{bookingId}/status`, `POST /bookings/{bookingId}/check-status`
- ApiService + DTOs + mapper + repository behind the existing seam; `ApiResult` -> `DomainResult` (EPIC-01-M1)
- Tests with Ktor `MockEngine`

## Note
Callback handling itself is server-side.

## Parent
[rusuh-documentation#25 FR-25](https://github.com/PT-Meeting-Sampai-Malam-Konsultasi/rusuh-documentation/issues/25)

## Depends on
EPIC-01 (M1..M7 as needed)
```

## 4. Suggested edits to existing tickets (not applied)

- **#30 FR-04-M4** (queue position / realtime) overlaps FR-06-M5, FR-07-M1, FR-09-M1, FR-10-M1. Either make #30 the umbrella over those four, or close it once they exist.
- **#38 FR-05-M4** (detail / booking API) overlaps FR-11, FR-12, FR-13, FR-15, FR-16, FR-17, FR-18, FR-19. Same choice.
- **#45 FR-06-M4** (Midtrans) overlaps FR-20, FR-21, FR-23, FR-25.
- **#21 FR-01-M4** already covers doc FR-01; FR-02/FR-03 add the state mapping.
- **#50 FR-APP-01-M4** stays (no doc FR).
- Drop `on-hold` from #45 and #50 only if you agree.

## 5. Questions

1. FR-04 / FR-05 / FR-06 titles collide with the app parents of the same number (Waiting Room / Detail / Payment). Fine to keep (FR-04-M5, FR-05-M5, FR-06-M5), or prefix API tickets, e.g. `FR-06-API1`?
2. Keep #30, #38, #45 as umbrellas, or close them in favour of the per-FR tickets?
3. Order: EPIC-01-M1..M7 first, then FR-02/03 with #21 (Home), then FR-04..10 (queue), FR-11..19 (detail/booking), FR-20..25 (payment, tickets).

Counts: 1 EPIC-01 parent + 7 slices + 23 FR tickets = 31 new issues.
