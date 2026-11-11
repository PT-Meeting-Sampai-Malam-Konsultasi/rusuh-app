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

`AppEnvironment { Mock, Staging, Production }` picks the backend. The platform entry point chooses it from its
build type and passes it to `initKoin(environment)` (there is no default, so release never falls back to the mock):

| Platform | Debug | Release |
|---|---|---|
| Android | `BuildConfig.APP_ENVIRONMENT = "Mock"` | `"Production"` (androidApp/build.gradle.kts) |
| iOS | `#if DEBUG` in `iOSApp.swift` passes `"Mock"` | `"Production"` |

`ApiConfig.forEnvironment(env)` returns three independently configured hosts: `baseUrl` (api), `queueBaseUrl`
(`q.`) and `payBaseUrl` (`pay.`). Every URL is validated at construction (blank, malformed or non-http(s) throws
`IllegalArgumentException` naming the property) and stored without a trailing slash.

- Mock: all three hosts share one Mockoon instance; the address differs per platform because of how the
  emulator reaches the host machine: Android emulator `http://10.0.2.2:3000`, iOS simulator
  `http://localhost:3000` (`platformMockBaseUrl`, `expect`/`actual`). A physical Android device can use
  `adb reverse tcp:3000 tcp:3000` (the debug config allows `localhost`) or the machine's LAN IP (add it to the debug
  `network_security_config.xml`). A physical iOS device cannot reach `localhost`: point it at another environment or
  a LAN URL.
- Staging and Production use `PLACEHOLDER_*` constants in `ApiConfig`, marked `shortcut:` because the real hosts
  are not known yet (the spec has no `servers`). They use the reserved `.invalid` TLD, which never resolves, so a
  build shipped without real hosts fails with a network error. To replace them, edit the constants in `ApiConfig`
  once the backend publishes the hosts and update `ApiConfigTest`. No secrets live in the repo.
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

Wiring of ApiServices, Repositories and Caches (`single`) and UseCases (`factory`) arrives with the feature tickets.

In tests, resolve clients on a mock engine by overriding the `HttpClient`, see `CoreModuleTest`.

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
host-independent plugins; each host client from `ApiClient.forHost(shared, baseUrl, hostAuth)` adds its token
plugins on top (`coreModule()` wires all of it).

Order (request direction): `ContentNegotiation`, `HttpTimeout`, `AcceptLanguagePlugin`, `ServerClockPlugin`,
`Logging` (shared client), then `AuthHeadersPlugin` and `SessionRefreshPlugin` (per host).

| Plugin | What it does |
|---|---|
| `AcceptLanguagePlugin` | `Accept-Language: id` or `en` from `LocaleProvider` (default `SystemLocaleProvider`: device language, anything but English is `id`, Android's legacy `in` included). Read per request. A header the call sets itself wins. |
| `ServerClockPlugin` | Reads `serverNow` (RFC 3339) from small `application/json` bodies, top level or one nested object, and re-anchors the `ServerClock`. Cheap: bodies without the text `"serverNow"` are not parsed, streams and downloads are skipped. |
| `AuthHeadersPlugin` | `X-Device-Token` and `Authorization: Bearer <access>` from `TokenStore` when present; never replaces a header the call set. Never logs values (`Logging` also masks the token headers). |
| `SessionRefreshPlugin` | On `401`: one single-flight `POST /auth/refresh`, then the same request once more with the new access token. |

### What an ApiService does

- Nothing for language, device token, Bearer, refresh or the clock: automatic.
- Queue and admission tokens are opt-in per call, never automatic:
  `api.get(EP) { queueToken(token) }`, `api.post(EP) { admissionToken(token) }` (`X-Queue-Token`, `X-Admission-Token`;
  read the value from `TokenStore` in the repository).
- Calls outside the session (the refresh call, OTP calls that must not carry a stale Bearer) add `skipSessionAuth()`:
  no Bearer and no 401 handling; the device token is still sent.
- Creating or charging POSTs add `idempotencyKey(key)`.

### Per-host policy (`HostAuth`)

`HostAuth(tokenStore, refreshCoordinator, sendDeviceToken, sendBearer)` is the per-host switch. Decision: all three hosts
(`api`, `queue`, `pay`) use the default (device token and Bearer sent, refresh on). The contract lists `bearerAuth` on the
payment endpoints as the alternative to the web-only `__Host-pay_session` cookie (mobile has no cookie session), the
queue endpoints need `X-Device-Token`, and the auth endpoints take the device token only: sending both headers
everywhere is harmless to hosts that ignore one. To change it for a host, pass a different `HostAuth` in `coreModule()`.

### 401 and refresh

`SessionRefreshCoordinator` (single-flight, one lock):

1. A 401 on a request that carried a Bearer, or any 401 while a refresh token is stored, asks the coordinator for a
   new access token. Concurrent 401s queue on the lock; the first calls refresh, the others see the new access token
   in `TokenStore` and reuse it, so the server sees exactly one refresh (reusing a rotated refresh token revokes the
   token family). Anonymous 401s (no Bearer, no refresh token) are returned untouched.
2. Success: the rotated refresh token is persisted, the access token set in memory, and the failed request is sent
   once more with `Authorization: Bearer <new>`. A second 401 is returned as is: no loop. Only the request the server
   already rejected is replayed, and a 401 means it was not processed, so no write is ever repeated.
3. Refresh rejected (400 or 401), or a session without any refresh token: `TokenStore.clearSession()` (access, refresh,
   queue, admission tokens; the device token stays) and `SessionEvents.events` emits `SessionEvent.SessionExpired`; the
   UI shell collects it once and routes to sign-in. Nothing is replayed to late collectors.
4. Refresh that could not complete (offline, timeout, 5xx): the session is kept and the original 401 is returned.
5. The refresh runs `NonCancellable`, so a cancelled caller cannot lose a rotated token.
6. The refresh call carries `skipSessionAuth()` and only the device token (the contract's security for `POST /auth/refresh`:
   the refresh token is in the body on mobile), so it never triggers the 401 handler itself.

`TokenRefresher` is the seam: the default `ApiTokenRefresher` calls `POST /auth/refresh` through the `api` client;
`AuthRepository.refresh` (#67) can replace it by rebinding `TokenRefresher`. Tests inject a fake lambda.

### Idempotency keys

`newIdempotencyKey()` returns a random UUID. Rule: one key per user attempt, the same key on every retry of that
attempt, a new key only for a new user attempt. Never auto-retry a charging or creating POST with a different key: that
is a second charge. Keep the key in the ViewModel/use case state for the attempt (not in the ApiService).

### Server clock

One clock, `MonotonicServerClock` (bound as `ServerClock` and `ServerTimeSink` in `coreModule()`): it stores the last
`serverNow` plus a monotonic mark, so `nowMillis()` follows the server and never the device clock. Every response with
`serverNow` re-anchors it (latest wins, which corrects drift); latency is not compensated (the clock runs about half a
round trip behind, which can only lengthen a countdown slightly). Until the first response it counts from 0 (the
Detail stub's behaviour). Read time only through `ServerClock`; `ServerClock.remainingMillis(expiresAtMillis)` is never
negative. Tests pass a `TestTimeSource` as `timeSource`.

### Testing the plugins

`testHttp(...)` in `commonTest/.../data/network/plugin/HttpPluginTestSupport.kt` builds an `ApiClient` on a `MockEngine`
with any of the language provider, clock sink and `HostAuth`; see `HeaderPluginsTest`, `SessionRefreshTest`, `ServerClockTest`.
