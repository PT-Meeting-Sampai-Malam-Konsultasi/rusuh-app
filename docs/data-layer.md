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
