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
Koin wiring (ApiService/Repository/Cache = `single`, UseCases = `factory`) arrives with the environment and base URL
config (EPIC-01-M2).

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
- Android read failures: a bad tag, invalidated key or corrupt entry is deleted and reads as null; other
  `GeneralSecurityException`s are treated as transient (null, entry kept). Failed writes throw.
- Never persisted: the access token, passwords, OTPs, NIK and payment data. Never log token values.
- Threat model: tokens are protected against other apps and against reading a device backup or file dump (private
  app storage and a Keystore key that is non-exportable and, on devices that support it, hardware-backed). A rooted or jailbroken device with a live
  process, or malware running as the app, can still use the keys; short access token lifetimes and server-side
  refresh token revocation are the mitigation.
- The platform actuals (Keystore, Keychain, SharedPreferences, NSUserDefaults) need a device or simulator, so
  they are not unit tested; they are covered by the later integration test. iOS compilation is checked by
  `compileKotlinIosSimulatorArm64`.
