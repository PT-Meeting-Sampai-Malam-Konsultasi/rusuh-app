# Data layer

How a network call travels from the API to the UI. Every API ticket builds on this; the rules come from the
steering docs (`data-layer.md`, `module-structure.md`, `dependency-injection.md`).

## Layers and result types

```
ApiService  --ApiResult<T>-->  Repository  --DomainResult<R>-->  UseCase  -->  ViewModel
(data/remote)                  (data/repository)                 (domain/usecase)
```

| Type | Package | Models |
|---|---|---|
| `ApiResult<T>` | `data/network` | HTTP outcome: `Success(code, data)`, `Error(code, ProblemDetails?, retryAfterSeconds?)`, `Failure(throwable)` |
| `ProblemDetails`, `ErrorCode` | `data/remote/dto`, `domain/model` | RFC 9457 `application/problem+json` body and its stable machine code |
| `DomainResult<T>` | `domain/model` | What the UI consumes: `Success`, `Error`, `Empty`, `NetworkError`, `TechnicalError` |

Success bodies are the payload itself, not an envelope. Endpoints that answer 204 use `ApiResult<Unit>`.
Any non-2xx status, 3xx and 304 included, is an `ApiResult.Error`.

Packages for new code: `data/remote` (ApiService, `dto/`, `mapper/`), `data/repository` (implementations and
`ApiResult.toDomainResult()`), `data/cache`, `domain/{model,repository,usecase}`, `di/`.
DTOs never leave the data layer; repositories map them to domain models.

## Writing an ApiService

```kotlin
class EventApiService(private val api: ApiClient) {
    suspend fun getEvents(): ApiResult<List<EventSummaryDto>> = api.get(EP_EVENTS)

    suspend fun cancelBooking(id: String): ApiResult<Unit> = api.post("$EP_BOOKINGS/$id/cancel")

    suspend fun createBooking(
        body: CreateBookingDto,
        key: String,
    ): ApiResult<BookingDto> =
        api.post(EP_BOOKINGS) {
            idempotencyKey(key)
            jsonBody(body)
        }

    suspend fun downloadTicket(id: String): ApiResult<ByteArray> = api.getBytes("$EP_TICKETS/$id/pdf")

    private companion object {
        const val EP_EVENTS = "/events"
        const val EP_BOOKINGS = "/bookings"
        const val EP_TICKETS = "/tickets"
    }
}
```

- Inject the shared `ApiClient`; never create an `HttpClient`.
- Endpoints are `const val EP_*` in a private companion object; no inline path literals.
- DTOs are `@Serializable`, with `@SerialName` on every field and a default for every field.
- Helpers: `get`, `post`, `put`, `patch`, `delete` (JSON), `getBytes` (raw bytes, e.g. PDF; no JSON `Accept` is
  forced), `jsonBody()` and `idempotencyKey()` request builders. Server-sent event streams are not wrapped: use
  `ApiClient.client` directly.
- None of them throws, except `CancellationException`, which is always rethrown.
- No default `Accept` header: ContentNegotiation adds it for JSON calls, raw calls choose their own.

## JSON rules

`ApiClient.defaultJson` is `ignoreUnknownKeys = true`, `explicitNulls = false`, `encodeDefaults = true`, and not
lenient.

- Unknown response members are ignored, so a new backend field never breaks parsing.
- `explicitNulls = false`: a null request member is omitted from the body instead of sent as `null`, and a `null`
  or missing response member takes the property default. Declare nullable DTO fields with `= null`.
- Not lenient: unquoted keys, single quotes and other non-standard JSON fail with a `SerializationException`, which
  becomes `ApiResult.Failure` (a `TechnicalError`). A 200 with an empty body for a non-`Unit` type fails the same way.
- Error bodies are parsed only for `application/problem+json` (case and charset parameters are ignored) and only up
  to 64 KB; anything else leaves `problem = null`.
- `Retry-After` is read in its seconds form only. The HTTP-date form is ignored (`retryAfterSeconds = null`).

## Mapping in a repository

```kotlin
override suspend fun getEvents(): DomainResult<List<Event>> =
    api.getEvents().toDomainResult { dtos -> dtos.map { it.toDomain() }.ifEmpty { null } } // null -> Empty
```

`DomainResult.Error` carries everything a ViewModel needs, so a repository never has to look at `ApiResult` itself:

- `code` (HTTP status) and `errorCode` (contract code, `UNKNOWN` for unseen values). Branch on these
  (e.g. 410 `EXPIRED`), never on message text.
- `extras`: extra problem members such as `attemptsLeft`, `categoryId`, `nikMasked`, as raw JSON.
- `traceId` for support, `retryAfterSeconds` for 429/503 back-off.
- `message` is server text for diagnostics only. It is not localized, so never show it as is.
- Offline, DNS failure and timeouts become `NetworkError` through the platform `isNetworkFailure()`
  (`expect`/`actual`: Android `java.io.IOException`, iOS Darwin request exceptions, Ktor timeouts); unreadable
  bodies and other exceptions become `TechnicalError`.
- Logic layers carry codes, not user-facing text. The UI maps `ErrorCode` to localized strings.

## Testing

Build an `ApiClient` on a Ktor `MockEngine` with the shared configuration:

```kotlin
ApiClient(baseUrl, client = HttpClient(MockEngine { respond(...) }) { configure(baseUrl) })
```

See `ApiClientTest` for success, 204, problem+json, 5xx, timeout, malformed body and cancellation examples.

## Environments, flavors and hosts

`AppEnvironment { Mock, Staging, Production }` picks the backend. Android has two product flavors in the `environment`
dimension:

| Flavor | `ENVIRONMENT` | `ALLOW_DEBUG_OPTIONS` | Launcher name |
|--------|---------------|-----------------------|---------------|
| `staging` | `"Staging"` | `true` | Rusuh Staging |
| `production` | `"Production"` | `false` | Rusuh |

Both flavors share the same `applicationId` (`com.rusuh.app.android`) and Firebase app.

The **staging** flavor in a **debug** build can switch between mock/stub/live:

| Option | Command | Environment + mode |
|--------|---------|-------------------|
| Mock (Mockoon) | `./gradlew :androidApp:assembleStagingDebug -PmockApi=true` | `Mock` + `Real` |
| Live staging | `./gradlew :androidApp:assembleStagingDebug -PmockApi=false` | `Staging` + `Real` |
| Stub (offline) | `./gradlew :androidApp:assembleStagingDebug -PbackendMode=Stub` | uses `Stub` mode |

The **production** flavor is **always** `Production` + `Real`, even in debug builds. The `-PmockApi` and `-PbackendMode`
flags have no effect on production variants. Gradle guards verify this:
- `verifyProductionFlavorHasNoDebugOptions`: checks `ALLOW_DEBUG_OPTIONS = false` and empty overrides in all production variants.
- `verifyReleaseNeverUsesMockApi`: checks all release variants have `DEBUG = false` and empty overrides.

Release builds (any flavor) always use `Production` + `Real`.

The platform entry points pass the flavor's `BuildConfig` fields to `initKoinForBuild`:
- Android `RusuhApplication` passes `DEBUG`, `ALLOW_DEBUG_OPTIONS`, `ENVIRONMENT`, `BACKEND_MODE` and `MOCK_API_OVERRIDE`.
- iOS `iOSApp.swift` calls `KoinIosKt.doInitKoin(isDebug:backendModeName:)` inside `#if DEBUG` / `#else`. iOS has no
  flavors yet (follow-up: xcconfig per environment); it behaves like the staging flavor (`allowDebugOptions = true`).

`NetworkConfigTest` covers every flavor × build-type × override combination.

`ApiConfig.forEnvironment(env)` returns three independently configured hosts: `baseUrl` (api), `queueBaseUrl`
(`q.`) and `payBaseUrl` (`pay.`). Every URL is validated at construction (blank, malformed or non-http(s) throws
`IllegalArgumentException` naming the property) and stored without a trailing slash.

- Mock: all three hosts share one Mockoon instance; the address differs per platform because of how the
  emulator reaches the host machine: Android emulator `http://10.0.2.2:3000`, iOS simulator
  `http://localhost:3000` (`platformMockBaseUrl`, `expect`/`actual`). A physical Android device can use
  `adb reverse tcp:3000 tcp:3000` (the debug config allows `localhost`) or the machine's LAN IP (add it to the debug
  `network_security_config.xml`). A physical iOS device cannot reach `localhost`: point it at another environment or
  a LAN URL.
- Staging and Production use the real base URL `https://api.rusuh-tiket.web.id/v1` (`ApiConfig.REAL_BASE_URL`; the `/v1` prefix comes from the staging web app configuration, the OpenAPI file lists no server). The queue
  (`q.`) and payment (`pay.`) hosts are not confirmed, so all three hosts use the same URL for now (`shortcut:` in
  `ApiConfig`); give them their own constants when the backend publishes them. The host is a skeleton: until the backend
  routes exist every call answers 404, which the app shows as its error states. `ApiConfig.usesPlaceholderHosts` (a
  `.invalid` host, which never resolves) stays for hosts added later; today it is false for every environment. No
  secrets live in the repo.
- Cleartext HTTP to the mock is allowed in debug only: Android `androidApp/src/debug` (manifest overlay plus
  `network_security_config.xml` for `10.0.2.2` and `localhost`; the release manifest has none), iOS an ATS exception
  for `localhost` in `iosApp/iosApp/Info-Debug.plist`, used only by the Debug configuration (keep it in sync
  with `Info.plist`; `scripts/check-info-plist.sh` runs in the Lint workflow and fails when they drift).
  `./gradlew :androidApp:verifyNoCleartextOutsideDebug` guards the Android side (part of `:androidApp:check`).

`coreModule()` (`di/CoreModule.kt`) owns the infrastructure: `ApiConfig`, one shared `HttpClient` (engine plus the
common plugins) and three `ApiClient`s qualified with `ApiClientQualifier.Api`, `.Queue` and `.Pay`, each pointing
at its host. Feature modules inject them and never build their own:

```kotlin
single { EventApiService(get(ApiClientQualifier.Api)) }
single { PaymentApiService(get(ApiClientQualifier.Pay)) }
```

`coreModule()` is a function, not a `val`: Koin keeps singleton instances in the module object, so each Koin start
and each test needs a fresh one.

### Adding a host

1. Add a `fooBaseUrl` property to `ApiConfig` (with `normalise`) and a value per environment in `forEnvironment`
   (Mock may reuse the mock URL).
2. Add `ApiClientQualifier.Foo = named("foo")` and a `single(ApiClientQualifier.Foo) { ApiClient.forHost(get(), get<ApiConfig>().fooBaseUrl) }`
   line to `coreModule()`.
3. Extend `ApiConfigTest` and `CoreModuleTest`. For a non-local mock host, also extend the debug cleartext config.

Wiring of ApiServices and Caches (`single`) and UseCases (`factory`) arrives with the feature tickets; repositories are bound per backend mode by `dataModules(mode)` (see below).

In tests, resolve clients on a mock engine by overriding the `HttpClient`, see `CoreModuleTest`.

## Backend modes: Stub, Mockoon, Real

### Live API (staging) notes

All services share `https://api.rusuh-tiket.web.id/v1`: core routes use `/core/...` (public event list and detail;
categories, tickets and bookings require auth), auth uses `/auth/...`, and queue uses `/queue/...`. Verified live
queue routes include `/queue/devices` and `/queue/{eventId}/join|me|frontier|claim|stream`; queue currently returns
404 `Unknown event` until the event queues are configured. `/core/me` and payments currently return 404.

Live differs from Apidog: `POST /auth/otp` rejects `consentVersion`; profile is `GET` and `PUT /auth/profile`;
mobile registration is `POST /queue/devices` and requires an `attestation` string. Staging accepts the temporary
`dev` value; release builds must replace it with native attestation. Event list responses wrap `items` and `serverNow`
and include extra nullable fields such as `venueShort`, `doorsOpenAt`, and `imageUrl`; decoders ignore unknown fields.

`AppEnvironment` says which hosts exist; `BackendMode { Stub, Real }` (`di/BackendMode.kt`, the `useStubs` switch) says
whether repositories talk to them. Three debug ways to run the app:

| Mode | Environment + mode | Network | Repositories | Needs |
|---|---|---|---|---|
| Stub (offline demo) | `Stub` (`-PbackendMode=Stub`; iOS scheme `BACKEND_MODE=Stub`) | None; in-process `StubEngine` | Stub repositories | Nothing |
| Mock (debug) | `Mock` + `Real` (`-PmockApi=true`; empty uses `MOCK_API`) | HTTP to Mockoon at `platformMockBaseUrl` | Real Home, Waiting Room, My Queues, Device, Auth, Detail, Tickets | `mockoon-cli start --data rusuh-mock.json -X` (`workspace-local/mockoon`) |
| Live staging (debug) | `Staging` + `Real` (`-PmockApi=false`) | HTTP to `https://api.rusuh-tiket.web.id/v1` | Same repositories as Mock | The staging backend |

Release builds always use `Production` + `Real` over HTTPS.

- Defaults: `BackendMode.defaultFor(env)` is `Real` for every environment. `Stub` is an explicit debug opt-in; asking
  for it for `Production` is rejected (`IllegalArgumentException`).
- Switch: `initKoin(environment, backendMode)` for tests and tools; apps use `initKoinForBuild` (see Environments).
- `dataModules(mode)` (`di/DataModule.kt`) returns `commonDataModule()` (ApiServices, device identity, auth state) plus
  `stubDataModule()` or `liveDataModule()`, which bind every repository interface and the `ServerClock`. In `Stub` it
  replaces the shared `HttpClient` with one on the in-process `StubEngine` (closed with Koin), so the three named
  `ApiClient`s and any ApiService built on them run with zero network, and the `ServerClock` is the demo's ticking
  `StubClock`, so countdowns run.
- Real repositories (`data/repository`): `RealHomeRepository` (`GET /core/events`), `RealQueueRepository` (behind both
  `WaitingRoomRepository` and `QueueRepository`: join, frontier polling at the ADR-0003 cadence, claim, leave),
  `RealMyQueuesRepository`, `RealDeviceRepository` (`POST /queue/devices`, the install id lives in the `SecureStore`),
  `RealTicketsRepository` (`GET /core/tickets`, `GET /core/tickets/{ticketId}`). They
  are built on `EventApiService`, `QueueApiService`, `DeviceApiService` and `TicketApiService` (`data/remote`), which use the `Api` and
  `Queue` clients (`/devices` and `/queue/*` belong to the queue service). Failed calls surface as `DomainException`
  (carrying the `DomainResult`), `SignInRequiredException` for a guest claim. `QueuePacing` makes the waits and the
  jitter injectable, so tests run in virtual time.
- `RealTicketsRepository`: fetches the signed-in user's tickets from `GET /core/tickets`, groups flat tickets by
  `bookingId` into orders, and enriches them with event facts from `GET /core/events/{eventId}` (cached per event in
  `TicketsCache` so the e-ticket page does not refetch). A 401 is handled by the session layer (refresh or session
  expired). shortcut: The Ticket schema does not include order number (`orderCode`), total, payment method, paid-at
  time, or contact email; these fields are left blank/placeholder in the domain model. The UI degrades gracefully.
- Temporary fallback (`shortcut:`): a repository without a real implementation resolves to its stub in `Real` mode and
  logs `WARNING: <Repository> has no real implementation yet ... STUB (fake data)` once. `PaymentRepository` is the
  only one left: the payment routes (`/core/payments/...`) are NOT deployed on the live staging backend (they return
  404), so it uses stub data. The constant `REPOSITORIES_STILL_ON_STUBS` (`DataModule.kt`) lists `PaymentRepository`;
  flipping to the real repository is a one-line change once the routes go live (replace the `stubOrFail` binding with
  `RealPaymentRepository(get())` and remove from the list). The constant `ALLOW_STUB_FALLBACK_UNTIL_REAL_REPOSITORIES`
  (default `true`) controls the fallback; set it to `false`, or pass `liveDataModule(allowStubFallback = false)`,
  for the strict behaviour: resolving a repository without a real implementation throws
  `RealRepositoryNotAvailableException`.
- Queue polling resilience: a transient failure inside the wait (network, 408, 429, 5xx) is retried with capped
  exponential backoff (2, 4, 8, 16, 30 s, `QueuePacing.backoffMillis`) that honours `Retry-After` (capped at 2 min); the
  flow fails after 5 failures in a row, or at once on a failure waiting cannot fix (401/403/404). Any success resets the
  count. The claim retries inside its 60 s window on the monotonic clock; a wait that would pass the window is cut to it
  and one last attempt follows before the place counts as missed. A claim 401 with problem code `UNAUTHORIZED` is
  "sign in required" (`SignInRequiredException`, `WaitingRoomErrorKind.SignInRequired`; `TODO(AUTH-00 #77)`), any other
  401 registers the device again once. After the pre-sale draw the number comes from `/me`: 410 ends the run, a 404 or a
  missing `seq` means "not drawn yet" (asked again at the next poll), and the random 0-30 s wait happens once.
- Claimed admission: the token goes to the `TokenStore`; its non-secret details (`slotId`, `maxTickets`, `expiresAt`)
  go to `QueueCache` as `AdmissionInfo` for the Detail screen. The Waiting Room keeps its own 60 s YourTurn countdown
  (the admission's longer order window is Detail's business), so the turn is not re-emitted after the claim. Leaving
  the queue clears the queue token and, when it belongs to that event, the admission token and details.
- Server time: `ServerClock.isSynced` is false until the first response carrying `serverNow`; then the clock counts from
  an arbitrary start. Unsynced, the queue uses the frontier's own `ts`, Home shows no countdown, and the device-token
  expiry is judged against the device wall clock with a one day margin (`wallClockMillis`, used for nothing else).
- My Queues reads entries in parallel (at most 4). An entry is dropped on 401/410/closed (and forgotten), left out but
  kept on 404 or a transient failure; the load fails only when every entry failed.
- `REPOSITORIES_STILL_ON_STUBS` (`DataModule.kt`, checked against the bindings by `DataModuleTest`) contains
  `PaymentRepository`: the payment routes are not deployed on live staging (404). `:androidApp:verifyReleaseNeverUsesMockApi` runs before `assembleRelease`.
- Mockoon (`workspace-local/mockoon`) simulates a moving queue: the frontier is SEQUENTIAL (served 100, 130, ..., two
  paused polls, then `served >= seq`), claim answers 409 `NOT_YET` once and then 200. Restart it to replay the sequence.
- SSE streaming (`data/repository/QueueStream.kt`, ADR-0015): when the frontier's `streamFrom > 0` and the user's
  position `<= streamFrom`, the repository opens an SSE stream (`GET /queue/{eventId}/stream`) for sub-second updates
  while polling continues as the source of truth. Streaming uses `bodyAsChannel` on a prepared request (not Ktor's SSE
  plugin, which has inconsistent Darwin support). The stream emits `frontier` (same schema) and `turn` (your turn)
  events parsed by `SseParser`. Fallback rules:
  - 2 failed connect attempts → fall back to polling, retry stream after 60 s (`SSE_RETRY_DELAY_MILLIS`).
  - No first event within 5 s (`SSE_FIRST_EVENT_TIMEOUT_MILLIS`) → fall back.
  - 30 s silence (`SSE_SILENCE_TIMEOUT_MILLIS`) → fall back (watchdog coroutine independent of collect).
  - 409 `TOO_FAR` → stop stream attempts permanently for this session.
  - 503 `STREAM_FULL` with `Retry-After` → back off, retry after the header value (capped at 5 min).
  - 410 `QUEUE_CLOSED/MISSED/EXPIRED` and 401 → handled by polling.
  - Server `retry:` field → updates the reconnect delay (capped between 3 s and 30 s).
  Timing constants live in `QueuePacing`. The stream auto-reconnects with `Last-Event-ID` when the server sends an `id`
  field. Cancelling the `observePlace` flow closes the stream. A claim guard (`claimStarted` flag) ensures only one
  claim attempt starts across both the stream and polling transports.
- Verified on the iOS simulator (Ktor 3.0.1 Darwin): the body arrives incrementally; the per-request timeout override keeps the stream alive.

### Stub payloads and the test harness (`data/stub`)

- `StubJson`: one JSON constant per endpoint, copied from the `example`s of the OpenAPI contract (synthetic data only,
  masked NIKs, example.com emails; `check-status` has no example in the contract, so it reuses the paid `Booking`).
  Keep them in sync by hand when the contract changes. `StubJsonTest` catches part of the drift: every body parses,
  problem bodies decode through `ProblemDetails`, key members are present and state/status values are inside the
  spec's enum lists. It is a safety net, not proof of conformance: success bodies are not decoded through DTOs until
  those exist, so each new DTO adds its decode check to the test.
- `StubResponses`: registry of `StubRoute(method, "/queue/{eventId}/frontier", response)`. `{param}` segments match any
  value (percent-decoded in `pathParams`); among scripted and default routes the one with the most fixed segments wins,
  a script wins a tie (`/bookings/active` stays served when `/bookings/{bookingId}` is scripted). Query strings are
  ignored: a filter such as `?status=` never changes the answer.
- `StubError`: named error responses for scenarios: 409 with problem+json and extras (`QuantityOverReservation` has
  `categoryId`, `WrongCode` has `attemptsLeft`), 410 queue errors, 422, 429 with `Retry-After: 30`, 503 with
  `Retry-After: 10`.
- `StubClock` (also a `ServerClock`): time-dependent members are placeholders (`@{now}`, `@{now+300}`, `@{epoch}`)
  rendered against the clock when served. Frozen by default (tests: `clock.advanceBy(2.minutes)` makes the next
  `expiresAt` later, no sleeping), starting at the spec's `serverNow`, 2026-11-14T10:40:00Z; `StubClock.ticking()` (the
  demo) also advances with a monotonic source.
- `StubEngine(clock, recordRequests = false)`: serves the routes and lets a scenario script a route:
  `stub.script(HttpMethod.Get, "/queue/{eventId}/frontier", responses)` serves them in order and repeats the last.
  Requests are recorded (`requests`: method, path, query, headers, body, path params; last 200) only with
  `recordRequests = true`, which tests opt into. Parallel requests are safe (one lock). A request no route serves
  answers a problem body with `code: STUB_NO_ROUTE` and the header `X-Stub-Miss: true` (404, or 405 when the path
  exists under another method), so "not stubbed" differs from a real NOT_FOUND.
- All client construction for both the real client and the stub goes through `buildHttpClient` / `buildHostClient`
  (`data/network/ClientBuilder.kt`): the one place to change when plugins or per-host defaults change.
- Tests (`commonTest/.../data/stub/StubTestSupport.kt`):

```kotlin
val stub = StubEngine(recordRequests = true)
val api = stub.apiClient()                                  // the real ApiClient on the stub engine
stub.script(HttpMethod.Get, "/queue/{eventId}/frontier", frontierSequence(stub.clock, 100, 140, 190))
stub.script(HttpMethod.Post, "/bookings", StubError.QuantityOverReservation)
// ... call the ApiService / repository under test ...
stub.assertRequest(0, HttpMethod.Get, "/queue/evt_1/frontier", mapOf("Accept" to "application/json"))
```

## Auth (email OTP, session)

Contract: the Apidog project "Rusuh Booking Tiket API" is the single reference (not the older OpenAPI file, not
the live behaviour).

```
AuthApiService --ApiResult--> RealAuthRepository --DomainResult--> use cases --> SignInViewModel / AccountViewModel
                                   |  tokens
                               SessionManager --> TokenStore (refresh: SecureStore, access: memory) + SessionEvents
```

- Flow: `requestOtp(email, fullName?, locale?)` (`POST /auth/otp`, 202 `challengeId`, `expiresAt`,
  `resendAfterSeconds`) then `verifyOtp(challengeId, code)` (`POST /auth/otp/verify`, 200 session). The request always
  omits `consentVersion` because staging rejects it. On success the access token is kept in memory, the
  refresh token goes to the `TokenStore`, the sticky `SessionEvents.expired` is reset and `SessionManager.isSignedIn`
  turns true, all before `verifyOtp` returns. The tokens are not part of the domain model.
- Device token (security scheme `deviceToken`): the repository tries `DeviceRepository.ensureRegistered()` before both
  OTP calls, best effort. Registration is `POST /queue/devices` with the required staging `attestation: "dev"`. A
  NetworkError from registration is returned as is. A 401 from the auth call forces one new registration and one retry.
- `isNewAccount = true`: after code verification, the name step stores the entered name locally and attempts
  `PUT /auth/profile`; the local value remains a fallback if the update fails.
- Profile: `GET /auth/profile` (`AuthRepository.me()`), with the Bearer.
- Refresh is not in `AuthRepository`: `ApiTokenRefresher` + `SessionRefreshCoordinator` refresh on 401 with the stored
  refresh token (single flight, rotation, `SessionExpired` on a rejected token); see "401 and refresh".
- `SessionManager.isSignedIn: StateFlow<Boolean>`: true after login, false after `logout()`, and false on
  `SessionEvent.SessionExpired`. Cold start: the access token is memory-only, so the initial value is restored
  asynchronously from "a refresh token is stored" (and no expiry pending); `isRestored` turns true when that read is
  done. `awaitSignedIn()` waits for it for at most 5 s; if the storage read hangs it returns the current value (false,
  signed out) instead of blocking, and a read that throws anything is treated as signed out. A login or logout that
  happens before the restore wins. The first call after a cold start has no Bearer, gets a 401 and refreshes.
  `startSession` runs to the end even if its caller is cancelled, and a login without a refresh token removes the
  stored one. `GetMyTicketsUseCase` follows the session: `isSignedIn = SessionManager::awaitSignedIn` (AppModule), so
  the Tiket tab shows its signed-out state until a login.
- `logout()`: snapshots the access and refresh tokens, clears the local session FIRST and non-cancellably (the UI is
  signed out at once and never waits for the network), then revokes on the server inside a 5 s bound. The returned
  result only describes the revoke attempt: read `SessionManager.isSignedIn` (already false while the revoke runs) to
  navigate. With an access token it calls `POST /auth/logout`; on a 401 (expired access token) or with no access token
  (cold start) it makes ONE `POST /auth/refresh` with the snapshot refresh token (session-less: no `SessionExpired`,
  the result is not stored, the cleared local state is untouched) and sends the logout with that access token. A
  refresh the server rejects, no refresh token, or a 401 on the logout itself all count as success (nothing left to
  revoke). A timeout or an unreachable server (also an unreachable refresh) is `NetworkError`: not revoked and not worth
  retrying, a rotated refresh token may stay valid on the server until it expires; one 5 s bound covers the refresh and
  the logout, and nothing from the refresh is stored. The logout call is marked `skipSessionAuth()` and carries only
  the snapshot Bearer, so a 401 is never replayed with a newer login's access token. Before the clear, `logout()`
  may wait up to 5 s for a hanging cold-start storage read (`awaitSignedIn()`). Known gap, closed by the state layer
  (see "State layer" below): a `verifyOtp` that completes after a logout would sign the user back in, so
  `VerifyOtpUseCase` drops such an answer and ends the session it started. No server call when not signed in. If clearing
  storage threw, the result is `TechnicalError`: the access token and the flag are gone but a refresh token may remain
  on disk, so offer a retry. The device token and the install id survive (`TokenStore.clearSession`).
- Session-less paths (no Bearer, no refresh, no `SessionExpired`): `/auth/otp`, `/auth/otp/verify`, `/auth/refresh`,
  `/devices`. `/me` is not (a 401 there refreshes), covered by `SessionRefreshTest`. `/auth/logout` takes the Bearer but is
  called by the repository after the local sign-out, with its own token snapshot (see `logout()`).
- Failures: read `DomainResult.authFailure()` (`AuthFailure`): `WrongCode(attemptsLeft)` (400 `WRONG_CODE` with
  `attemptsLeft`; the repository also maps an unmapped 409 on verify to it), `ChallengeExpired` and `ChallengeLocked` (410), `RateLimited(retryAfterSeconds)` (429),
  `InvalidInput` (400), `Unauthorized` (401), `Network`, `Unknown(status)`. The contract code decides; an unknown
  code falls back to the status for 400, 401, 429, and a 410 with an unknown code counts as expired.
- Stub mode: `StubAuthRepository` (hand-written, in memory, real `SessionManager`): code `123456` signs in, `000000`
  is "expired", any other code is a wrong code with `attemptsLeft` 4, 3, 2, 1 and then locked (as after five wrong
  codes). A first sign-in without `fullName` is `isNewAccount = true` with no name; a repeat sign-in of the same email
  is not new. Nothing is persisted: the stub's `SessionManager` uses an in-memory `TokenStore`, so its placeholder
  tokens never reach the secure store and a stub run starts signed out. Real mode has no fallback to it.
- TODO(EPIC-01-M5 #66): the OTP gate headers (`X-Turnstile-Token`, `X-Queue-Token`, `X-Admission-Token`) are not sent yet.

### State layer (AUTH-00-M3 #77, ACCOUNT-00 #104)

```
SignInViewModel   --> RequestOtpUseCase, VerifyOtpUseCase, SaveDisplayNameUseCase, ObserveSessionUseCase (+ ServerClock)
AccountViewModel  --> GetProfileUseCase, SignOutUseCase, ObserveSessionUseCase
AccountRoute      = both ViewModels -> AccountBody (login | loading | profile | retry)
```

ViewModels only call use cases (`domain/usecase/AuthUseCases.kt`) over `AuthRepository`, `DisplayNameStore` and the
`SessionObserver` (a read-only view of `SessionManager`: `phase` Restoring/SignedOut/SignedIn, `expired` items). Both
ViewModels are Koin `factory`s, kept per navigation back-stack entry (`SignInViewModelHolder`, `AccountViewModelHolder`)
so a step survives rotation and tab switches; the work is cancelled when the entry is dropped.

- Sign-in steps map onto the `SignInUiState` seam (Email, Code, WrongCode, CodeExpired, NewAccount); what that seam
  does not cover is `SignInStatus` (in flight, and one inline `SignInError`: network, 429 with a countdown, 400, unknown;
  localised copy, never server text).
- Flow: `requestOtp(email trimmed, locale = app language)` -> Code step. The resend cooldown is `resendAfterSeconds`; the
  validity countdown runs to `expiresAt` on the server clock (`ServerClock`, never the device clock; when the clock has
  not seen the server yet the design's 5 minutes count from receipt). One ticker coroutine runs only while a code step or
  a 429 countdown is on screen. `verifyOtp`: success with `isNewAccount = false` finishes (`SignedIn(isNewAccount =
  false)`); `isNewAccount = true` opens the name step (the user is already signed in, so the Account tab keeps the login
  on screen until "Lanjut", see `SignInState.holdsAccountPage`); `WRONG_CODE` -> WrongCode with the server's
  `attemptsLeft` (counted locally when missing); `CHALLENGE_EXPIRED` and `CHALLENGE_LOCKED` -> CodeExpired carrying the
  last code; resend opens a new challenge; "Ganti email" goes back to the Email step.
- One request at a time: while a request is in flight every intent is ignored (double taps, the auto-submit of the last
  digit plus the button). A 429 with `Retry-After` disables the main button until the countdown ends.
- Race with logout: `SignOutUseCase` bumps `AuthGeneration` first; `VerifyOtpUseCase` compares it with the value it
  started with and, if it moved, drops the answer and calls `logout()` again when the answer had signed in
  (`VerifyOtpOutcome.Superseded`). The sign-in ViewModel resets to a fresh Email step when the session goes from
  signed in to not signed in.
- Account: `AccountViewModel` follows the phase, loads `GET /auth/profile` when the route asks (not during the name step), and
  signs out (local session first, per `logout()`). Cold start shows a neutral state until the stored session is read
  (bounded by `awaitSignedIn`, then signed out); `SessionExpired` returns to the login with a short message. `/me`
  failure is a small retry state. "Hapus akun" stays inert, "Ganti email" is hidden.
- `shortcut:` the new account's name has NO API (the Apidog contract has no profile update). It is kept on the device in
  `AuthCache` (`auth_display_name` + `auth_display_name_user`, non-sensitive preferences, bound to the user id) and used
  when `/me` has no name (else the part of the email before the `@`). Sign-out removes it. To confirm with the backend.
- `shortcut:` the consent checkbox of the name step is not sent anywhere; the consent version travels with the code
  request.
- The in-progress name step is not saved across process death. If the app is killed or the user leaves that step, the
  session stays signed in and the profile shows the email prefix; this is acceptable for the POC.
- Open question for the backend: may `fullName` be omitted on `POST /auth/otp` for a new email? Apidog says it is
  "required only if the email has no account yet".

### Known live divergences (2026-11-12, `https://api.rusuh-tiket.web.id/v1`)

The app follows the verified live staging behavior for the paths below.

- The live backend rejects `consentVersion`, so the app omits it from `POST /auth/otp`.
- Profile reads and name updates use `GET` and `PUT /auth/profile`; the app stores a local name fallback before attempting the update.
- Device registration uses `POST /queue/devices` with `attestation: "dev"` until native attestation is available.
- Auth errors come as problem+json with `type: "about:blank"` (not an `api.rusuh.id/problems/...` URI), an Indonesian
  `detail` and sometimes a code outside the contract: `400` `Validasi gagal: "email" is required` with code
  `BAD_REQUEST`, `401` `Token akun tidak ditemukan` with `UNAUTHORIZED`, `410` titled `OTP challenge expired` with
  `CHALLENGE_EXPIRED`. `ProblemDetails` never reads `type` or `title` for a decision; `BAD_REQUEST` parses to
  `ErrorCode.UNKNOWN` (kept in `rawCode`) and still lands on `AuthFailure.InvalidInput` through the 400 status.
  `detail` is for diagnostics, never shown. Fixtures of these bodies are in `AuthHarness.kt`.
- Result in the app: with live staging selected, the code request follows the accepted live payload;
  the sign-in screen shows the inline "request rejected" line and stays usable (no crash). The profile screen reads the live `/auth/profile` response.

## Storage

| Data | Where | Persisted |
|---|---|---|
| Access token | `TokenStore.accessToken` (memory) | Never |
| Refresh, device, admission tokens, queue token per event (`queue_token_<eventId>`) | `SecureStore` via `SecureTokenStore` | Yes, encrypted |
| Non-sensitive cache (JSON payloads, flags) | `AppPreferences` via a module `*Cache` | Yes, plain |

- `SecureStore` (`suspend` get/put/remove/clear): Android keeps an AES-256-GCM key in the AndroidKeyStore (not
  exportable) and stores `base64(iv + ciphertext)` in private SharedPreferences, with the entry name bound as AAD.
  We do not use `androidx.security:security-crypto` (`EncryptedSharedPreferences`): it is deprecated and no longer
  maintained, while the KeyStore AES-GCM approach needs no extra dependency. iOS uses the Keychain
  (`kSecClassGenericPassword`, service = bundle id, `AfterFirstUnlockThisDeviceOnly`: no iCloud sync, no restore
  to another device). An undecryptable Android entry reads as `null` and is deleted, so the user simply signs in again.
- `AppPreferences` is SharedPreferences / NSUserDefaults. Booleans are stored as `"true"`/`"false"`.
- Interfaces live in `data/storage`; platform classes are bound in `platformModule`, the rest in `storageModule`.
  Tests use in-memory fakes of both interfaces (`FakeStorage.kt`).
- Cache pattern: extend `PrefsCache(prefs, prefix)` with the feature prefix (`home_`, `queue_`, `book_`, `tix_`,
  `auth_`). The key strings are constants inside the owning cache and nowhere else; keys outside the prefix are
  rejected. `HomeCache` is the example. Secure keys are owned by `SecureTokenStore`.
- Logout: `TokenStore.clearSession()` clears the access token first, then removes the refresh, admission and all
  `queue_token_` entries (via `SecureStore.removeWhere`), attempting every removal even if one fails and rethrowing
  the first failure. The device token is never touched, because it identifies the install, not the session. Token
  writes and logout share one lock.
- Backup and restore: the `rusuh_secure_store` file is excluded from cloud backup and device transfer
  (`backup_rules.xml`, `data_extraction_rules.xml`), since the Keystore key does not move between devices. After a
  restore the user signs in again and the app registers a new device token.
- iOS fresh install: the Keychain survives an uninstall but NSUserDefaults does not. `KeychainSecureStore` keeps a
  first-launch flag in NSUserDefaults and wipes this service's Keychain items once when the flag is missing.
  Keychain errors other than "not found" throw `KeychainException` (status only) instead of reading as a miss.
- Android read failures: a bad tag, invalidated key or structurally corrupt entry (`IllegalArgumentException`: bad
  base64, too short, malformed parameters) is deleted and reads as null; every other failure
  (`GeneralSecurityException`, `ProviderException`, `IllegalStateException`, ...) is treated as transient: null, entry
  kept. Failed writes throw.
- Logout is cancellation-proof (`NonCancellable`), attempts every removal and rethrows the first failure with the
  others as suppressed. `SecureTokenStore` keeps a session generation bumped by `clearSession`: a refresh, admission
  or queue token write that started before a logout and runs after it is discarded, so it cannot resurrect a token.
- Never persisted: the access token, passwords, OTPs, NIK and payment data. Never log token values.
- Threat model: tokens are protected against other apps and against reading a device backup or file dump (private
  app storage and a Keystore key that is non-exportable and, on devices that support it, hardware-backed). A rooted or jailbroken device with a live
  process, or malware running as the app, can still use the keys; short access token lifetimes and server-side
  refresh token revocation are the mitigation.
- The platform actuals (Keystore, Keychain, SharedPreferences, NSUserDefaults) need a device or simulator, so
  they are not unit tested; they are covered by the later integration test. iOS compilation is checked by
  `compileKotlinIosSimulatorArm64`.

## HTTP plugins

Every request on every host goes through the same plugin chain. The shared `HttpClient` (one engine) installs the
host-independent plugins (`ApiClient.configurePlugins(...)`); each host client from
`ApiClient.forHost(shared, baseUrl, hostAuth)` adds its token plugins on top (`coreModule()` wires all of it).
Redirects are not followed (`followRedirects = false`): a 3xx is an `ApiResult.Error`, so token headers can never be
replayed to another host.

Order (request direction): `ContentNegotiation`, `HttpTimeout`, `AcceptLanguagePlugin`, `ServerClockPlugin`,
`Logging` (shared client), then `AuthHeadersPlugin` and `SessionRefreshPlugin` (per host).

| Plugin | What it does |
|---|---|
| `AcceptLanguagePlugin` | `Accept-Language: id` or `en` from `LocaleProvider` (default `SystemLocaleProvider`: device language, anything but English is `id`, Android's legacy `in` included). Read per request. A header the call sets itself wins. |
| `ServerClockPlugin` | Marks the send time of each request and reads `serverNow` (RFC 3339) from small `application/json` bodies (top level, a direct member object, or the first element of a member array that has it, e.g. `GET /bookings/active`). Bodies without the text `"serverNow"` are not parsed; streams, downloads and bodies over 256 KB (by received byte count) are skipped. |
| `AuthHeadersPlugin` | `X-Device-Token` and `Authorization: Bearer <access>` from `TokenStore` when present; never replaces a header the call set. Never logs values. |
| `SessionRefreshPlugin` | On `401`: one single-flight `POST /auth/refresh`, then the same request once more with the new access token. |
| `Logging` (opt-in) | Level is injectable (`logLevel`, default INFO). `Authorization`, `X-Device-Token`, `X-Queue-Token`, `X-Admission-Token`, `X-Turnstile-Token`, `Cookie` and `Set-Cookie` are masked at any level, matched case-insensitively. |

### What an ApiService does

- Nothing for language, device token, Bearer, refresh or the clock: automatic.
- Queue and admission tokens are opt-in per call, never automatic:
  `api.get(EP) { queueToken(token) }`, `api.post(EP) { admissionToken(token) }` (`X-Queue-Token`, `X-Admission-Token`;
  read the value from `TokenStore` in the repository).
- Outside the session by default (no Bearer, no refresh, no `SessionExpired`; the device token is still sent): the
  paths ending in `/auth/otp`, `/auth/otp/verify`, `/auth/refresh` and `/devices`. A 401 from
  verify-OTP is a wrong code, not an expired session. Any other such call adds `skipSessionAuth()`.
- Creating or charging POSTs add `idempotencyKey(key)`.

### Per-host policy (`HostAuth`)

`HostAuth(tokenStore, refreshCoordinator, sendDeviceToken, sendBearer)` is the per-host switch. Decision: all three hosts
(`api`, `queue`, `pay`) use the default (device token and Bearer sent, refresh on). The contract lists `bearerAuth` on the
payment endpoints as the alternative to the web-only `__Host-pay_session` cookie (mobile has no cookie session), the
queue endpoints need `X-Device-Token`, and the auth endpoints take the device token only: sending both headers
everywhere is harmless to hosts that ignore one. To change it for a host, pass a different `HostAuth` in `coreModule()`.

### 401 and refresh

`SessionRefreshCoordinator` (single-flight):

1. A 401 on a request that carried a Bearer, or any 401 while a refresh token is stored, asks the coordinator for a
   new access token. All 401s that arrive while a refresh runs await the SAME in-flight refresh and get the same
   result (success or failure), so the server sees exactly one refresh (reusing a rotated refresh token revokes the
   token family). The refresh runs in the coordinator's own scope: cancelling one waiting request does not cancel it
   for the others. Anonymous 401s (no Bearer, no refresh token) are returned untouched.
2. Success: the rotated refresh token is persisted, the access token set in memory, and the failed request is sent
   once more with `Authorization: Bearer <new>`. A second 401 is returned as is: no loop. Only the request the server
   already rejected is replayed, and a 401 means it was not processed, so no write is ever repeated.
3. Refresh rejected (401 only; a 400 is not treated as a dead session), or a session without any refresh token:
   `TokenStore.clearSession()` (access, refresh, queue, admission tokens; the device token stays) and
   `SessionEvents.events` emits `SessionEvent.SessionExpired`. Tokens are cleared only if the stored refresh token is
   still the one that was rejected, so a newer login is never wiped.
4. `SessionEvents.expired` (`StateFlow<Boolean>`) is the sticky twin of the event: true from the expiry until a login.
   The UI shell reads it on start (so an expiry with no collector attached is not lost) and also collects `events`.
   It resets on the next request that carries an access token, or explicitly with `SessionEvents.reset()` (call it
   when a login stores its tokens: `SessionManager.startSession` does it).
5. Refresh that could not complete (offline, timeout, 5xx, 400): the session is kept, the original 401 is returned,
   and a burst of further 401s does not start another attempt for 5 seconds (`unavailableCooldown`).
6. The refresh runs `NonCancellable` and carries only the device token (the contract's security for
   `POST /auth/refresh`: the refresh token is in the body on mobile), so it never triggers the 401 handler itself.

`TokenRefresher` is the seam: the default `ApiTokenRefresher` calls `POST /auth/refresh` through the `api` client;
`AuthRepository` has no refresh: the session layer owns it (the login stores the refresh token the way a refresh does),
and `TokenRefresher` can be rebound to replace the call. Tests inject a fake lambda.

### Idempotency keys

`newIdempotencyKey()` returns a random UUID. Rule: one key per user attempt, the same key on every retry of that
attempt, a new key only for a new user attempt. Never auto-retry a charging or creating POST with a different key: that
is a second charge. Keep the key in the ViewModel/use case state for the attempt (not in the ApiService).

**When to regenerate:**
- New user attempt (fresh form submission, after fixing a validation error that requires user action).
- Non-recoverable errors (admission expired, sold out) where the user must start over.

**When to keep the same key:**
- Transient errors (`NetworkError`, `ServiceUnavailable`, `RateLimited`): the original request may have succeeded
  server-side, and the server de-duplicates by key. The user tapping "try again" after a timeout should use the
  same key, not create a second booking.
- The `DetailViewModel` keeps the key across network failures and only regenerates it for errors that require user
  action (form changes, re-queueing).

### Server clock

One clock, `MonotonicServerClock` (bound as `ServerClock` and `ServerTimeSink` in `coreModule()`): it stores the last
`serverNow` plus a monotonic mark, so `nowMillis()` follows the server and never the device clock.

- Latency: each request's send time is marked; the anchor is `serverNow + (receivedAt - sentAt) / 2` (midpoint
  estimate, error at most half the round trip).
- Order: a response whose request was sent before the request of the current anchor is ignored, so a slow old response
  arriving late never moves the clock backwards. (A request retried after a 401 has no send mark and counts as sent on
  arrival.)
- Until the first response it counts from 0 (the Detail stub's behaviour) and `isSynced` is false: gate on `isSynced`
  wherever a wrong time matters (a payment countdown).
- Read time only through `ServerClock`; `ServerClock.remainingMillis(expiresAtMillis)` is never negative. Tests pass a
  `TestTimeSource` as `timeSource`.

### Testing the plugins

`testHttp(...)` in `commonTest/.../data/network/plugin/HttpPluginTestSupport.kt` builds an `ApiClient` on a `MockEngine`
with any of the language provider, clock sink, `HostAuth` and a log level; see `HeaderPluginsTest`,
`SessionRefreshTest`, `ServerClockTest`.

### Building clients (`ClientBuilder`)

`buildHttpClient(engine, localeProvider, serverTime, logLevel)` and `buildHostClient(shared, baseUrl, hostAuth)` in
`data/network/ClientBuilder.kt` are the only places that assemble the plugin chain. `coreModule()` passes the real
locale provider, the `MonotonicServerClock` as `ServerTimeSink` and a `HostAuth`; the `StubEngine` calls them without
auth or clock sink (stub mode has no session, and its `ServerClock` is the ticking `StubClock`). In `BackendMode.Real`
the `ServerClock` consumers read the same `MonotonicServerClock` instance the response plugin updates.
