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

## Environments and hosts

`AppEnvironment { Mock, Staging, Production }` picks the backend. The ONE switch between the local mock and the real
host is `NetworkConfig.MOCK_API` (`data/network/NetworkConfig.kt`): edit the constant and rebuild.

| Build | `MOCK_API = true` | `MOCK_API = false` |
|---|---|---|
| Debug | `Mock` + `BackendMode.Real`: HTTP to the local Mockoon | `Staging` + `BackendMode.Real`: HTTP to the real host |
| Release | `Production` + `Real` (the flag is ignored) | `Production` + `Real` |

The platform entry points only say whether the build is debug and pass the optional debug-only backend mode override
to `initKoinForBuild(isDebug, backendModeName)`, which asks `NetworkConfig.startup`: Android `RusuhApplication` passes
`BuildConfig.DEBUG` and `BuildConfig.BACKEND_MODE`, iOS `iOSApp.swift` calls `KoinIosKt.doInitKoin(isDebug:backendModeName:)`
inside `#if DEBUG` / `#else`. A release build can never select the mock or stub data: `startup` ignores the flag and
the override unless `isDebug`, `NetworkConfigTest` covers every combination, and `./gradlew
:androidApp:verifyReleaseNeverUsesMockApi` (part of `:androidApp:check`) checks the generated release `BuildConfig`
(`DEBUG = false`, empty `BACKEND_MODE`).

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

Wiring of ApiServices and Caches (`single`) and UseCases (`factory`) arrives with the feature tickets; repositories are bound per backend mode in `dataModule` (see below).

In tests, resolve clients on a mock engine by overriding the `HttpClient`, see `CoreModuleTest`.

## Backend modes: Stub, Mockoon, Real

`AppEnvironment` says which hosts exist; `BackendMode { Stub, Real }` (`di/BackendMode.kt`, the `useStubs` switch) says
whether repositories talk to them. Four ways to run the app:

| Mode | Environment + mode | Network | Repositories | Needs |
|---|---|---|---|---|
| Mockoon over HTTP (the debug default) | `Mock` + `Real` (`MOCK_API = true`) | HTTP to the Mockoon mock at `platformMockBaseUrl` | Real Home, Waiting Room, My Queues, Device; stub Detail and Tickets (WARNING once each) | `mockoon-cli start --data rusuh-mock.json -X` (`workspace-local/mockoon`) |
| Real host (debug) | `Staging` + `Real` (`MOCK_API = false`) | HTTP to `https://api.rusuh-tiket.web.id/v1` | Same as above | The backend (queue and auth routes answer; core/booking routes 404 as of 2026-11-12) |
| Release | `Production` + `Real` | HTTP to the real host | Same as above | The backend |
| Stub (in process, offline demo) | `Mock`/`Staging` + `Stub` (debug only, `-PbackendMode=Stub` or `BACKEND_MODE=Stub` in the Xcode scheme) | None: the HTTP client runs on `StubEngine` | Stub repositories | Nothing |

- Defaults: `BackendMode.defaultFor(env)` is `Real` for every environment. `Stub` is an explicit debug opt-in; asking
  for it for `Production` is rejected (`IllegalArgumentException`).
- Switch: `initKoin(environment, backendMode)` for tests and tools; apps use `initKoinForBuild` (see Environments).
- `dataModule(mode)` (`di/DataModule.kt`) binds every repository interface and the `ServerClock` per mode. In `Stub` it
  replaces the shared `HttpClient` with one on the in-process `StubEngine` (closed with Koin), so the three named
  `ApiClient`s and any ApiService built on them run with zero network, and the `ServerClock` is the demo's ticking
  `StubClock`, so countdowns run.
- Real repositories (`data/repository`): `RealHomeRepository` (`GET /events`), `RealQueueRepository` (behind both
  `WaitingRoomRepository` and `QueueRepository`: join, frontier polling at the ADR-0003 cadence, claim, leave),
  `RealMyQueuesRepository`, `RealDeviceRepository` (`POST /devices`, the install id lives in the `SecureStore`). They
  are built on `EventApiService`, `QueueApiService` and `DeviceApiService` (`data/remote`), which use the `Api` and
  `Queue` clients (`/devices` and `/queue/*` belong to the queue service). Failed calls surface as `DomainException`
  (carrying the `DomainResult`), `SignInRequiredException` for a guest claim. `QueuePacing` makes the waits and the
  jitter injectable, so tests run in virtual time.
- Temporary fallback (`shortcut:`): a repository without a real implementation resolves to its stub in `Real` mode and
  logs `WARNING: <Repository> has no real implementation yet ... STUB (fake data)` once. Detail and Tickets are the
  ones left. The constant `ALLOW_STUB_FALLBACK_UNTIL_REAL_REPOSITORIES` (`DataModule.kt`, default `true`) controls it;
  set it to `false`, or pass `dataModule(mode, allowStubFallback = false)`, for the strict behaviour: resolving such a
  repository throws `RealRepositoryNotAvailableException`. Remove the constant and fallback when Tickets
  (TICKET-00-M4) and Detail (#80 #81 #82) land: a feature ticket then binds its real repository in place of that
  repository's `notYetReal(...)` line.
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
- Release builds still show FAKE Detail and Tickets data through the fallback above (one WARNING each). The list is
  `REPOSITORIES_STILL_ON_STUBS` (`DataModule.kt`, checked against the bindings by `DataModuleTest`): currently
  `TicketsRepository` and `DetailRepository`. `:androidApp:verifyReleaseNeverUsesMockApi` runs before `assembleRelease`.
- Mockoon (`workspace-local/mockoon`) simulates a moving queue: the frontier is SEQUENTIAL (served 100, 130, ..., two
  paused polls, then `served >= seq`), claim answers 409 `NOT_YET` once and then 200. Restart it to replay the sequence.
- TODO(FR-04-M4 #30): the last 2,000 places also get SSE (ADR-0015); only polling is implemented.

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
  paths ending in `/auth/otp`, `/auth/otp/verify`, `/auth/otp/request`, `/auth/refresh` and `/devices`. A 401 from
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
   when the auth data layer stores a login, #67).
5. Refresh that could not complete (offline, timeout, 5xx, 400): the session is kept, the original 401 is returned,
   and a burst of further 401s does not start another attempt for 5 seconds (`unavailableCooldown`).
6. The refresh runs `NonCancellable` and carries only the device token (the contract's security for
   `POST /auth/refresh`: the refresh token is in the body on mobile), so it never triggers the 401 handler itself.

`TokenRefresher` is the seam: the default `ApiTokenRefresher` calls `POST /auth/refresh` through the `api` client;
`AuthRepository.refresh` (#67) can replace it by rebinding `TokenRefresher`. Tests inject a fake lambda.

### Idempotency keys

`newIdempotencyKey()` returns a random UUID. Rule: one key per user attempt, the same key on every retry of that
attempt, a new key only for a new user attempt. Never auto-retry a charging or creating POST with a different key: that
is a second charge. Keep the key in the ViewModel/use case state for the attempt (not in the ApiService).

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
