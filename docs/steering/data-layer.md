---
inclusion: fileMatch
fileMatch:
  - "**/data/**"
  - "**/domain/repository/**"
  - "**/ApiService*"
  - "**/Cache*"
---
# Data Layer

## API Service

```kotlin
class ShopApiService(private val apiService: MyTelkomselApiService) {
    // Status-aware (preferred) — see "Three result types" below.
    suspend fun getCategories(): ApiResult<ApiResponse<CategoriesData>> =
        apiService.tryGetWithStatus(EP_CATEGORIES)

    private companion object {
        const val EP_CATEGORIES = "/offers/categories"
    }
}
```

- Constructor: inject the shared `MyTelkomselApiService` (or `HttpClient` + `baseUrl`). Never create your own `HttpClient`.
- **Preferred: return `ApiResult<T>`** via the status-aware helpers (`tryGetWithStatus`, …). The HTTP status is preserved, so repositories can branch on specific codes (401/404/410/…). This is the direction the codebase is migrating to (pilot: `:features:shop`).
- **Legacy: `T?`** via `tryGet`/`tryPost` (catch internally, non-2xx → `null`). Still valid for existing code, but `null` erases the HTTP status — a 410 is indistinguishable from a timeout. Do NOT add new endpoints on the `T?` pattern when the caller needs to distinguish error codes.
- DTOs: `@Serializable`, `@SerialName` on every field, all fields have defaults.
- **Endpoint paths are `const val` in a `private companion object` at the bottom of the ApiService** (prefix `EP_`). Never inline raw endpoint string literals in method bodies. One ApiService owns its own endpoints (don't share across services).

## ApiResponse Envelope

All network responses use the shared `ApiResponse<T>` envelope from `:features:core` — the payload always lives under `.data`:

```kotlin
@Serializable
data class ApiResponse<T>(
    @SerialName("status") val status: String = "",
    @SerialName("statusCode") val statusCode: String = "",
    @SerialName("statusDesc") val statusDesc: String = "",
    @SerialName("message") val message: String? = null,
    @SerialName("data") val data: T? = null,
    @SerialName("transaction_id") val transactionId: String = "",
    @SerialName("meta") val meta: Meta? = null,          // requestId?/timestamp/responseTimeMs/cached
    @SerialName("partial") val partial: Boolean = false, // graceful degradation flag
    @SerialName("errors") val errors: List<ApiError>? = null, // ApiError{code,message}
)
```

- **Two envelope styles, one type.** `status` may be `"success"` **or** a numeric-string code (`"00000"` success / `"40000"` etc.). Both deserialize into the same `ApiResponse<T>`.
- **Payload under `.data`** always. ApiService methods return the DTO **nullable** (`ApiResponse<T>?`); repositories read `?.data`, map to domain, and emit `DomainResult`.
- **`meta` / `transaction_id` / `partial` / `errors` are optional and tolerated** — all nullable/defaulted, so responses that omit them deserialize fine. `meta` models only the verified common subset; `errors[]` carries per-slot `{code,message}` on partial (Style B) responses.
- Use `Json { ignoreUnknownKeys = true }` at the client — surplus backend fields never break deserialization.

## Three result types — one per layer

Do NOT invent a per-endpoint result type (e.g. `FooDetailResult { Success/Error/... }`). The codebase already has one type per layer; compose them:

| Type | Layer | Models | Package |
|---|---|---|---|
| `ApiResponse<T>` | **wire / body** | the JSON envelope (`status`, `data`, `message`, `meta`, `errors`, …) | `features.core.model` |
| `ApiResult<T>` | **transport / HTTP** | the call outcome + **HTTP status**: `ApiResultSuccess(code, data)` / `ApiResultError(code, JsonObject)` / `ApiResultException(throwable)` | `features.core.data.base` |
| `DomainResult<T>` | **domain** | the result the UI consumes: `Success(data)` / `Error(message, code)` / `Empty` / `NetworkError` / `TechnicalError` | `features.core.domain.base` |

These are **orthogonal axes, not alternatives** — a status-aware call naturally returns `ApiResult<ApiResponse<T>>` (transport outcome wrapping the body envelope), and the repository maps that down to `DomainResult<DomainModel>`.

```kotlin
// Repository: ApiResult<ApiResponse<T>>  ->  DomainResult<DomainModel>
override suspend fun getOfferDetail(id: String): DomainResult<PackageDetail> =
    when (val r = api.fetchOfferDetail(id)) {          // ApiResult<ApiResponse<OfferDetailDto>>
        is ApiResultSuccess -> r.data?.data?.offer?.toDomain()
            ?.let { DomainResultSuccess(it) } ?: DomainResultError(code = r.code)
        is ApiResultError   -> DomainResultError(code = r.code, message = r.message)  // 410 etc. via code
        else                -> DomainResultNetworkError()
    }
```

Rules:
- **Never** create a bespoke `sealed Result` per endpoint. Reuse `DomainResult` — HTTP semantics (e.g. `410 OFFER_CONTEXT_EXPIRED`) ride on `DomainResultError.code`, and the ViewModel branches on the code (`if (code == 410) NavEffect.Back`).
- `ApiResponse` is the **body only** — it has no HTTP status, so it alone cannot distinguish a 410 from a 500 or a network error. When the caller must react to a status code, the ApiService MUST return `ApiResult` (status-aware), not `ApiResponse?`.
- Migration direction: new/changed API calls return `ApiResult`; repositories map to `DomainResult`. `:features:shop` is the pilot; other modules follow.

## Repository (Cache-First)

```kotlin
override fun getCategories(): Flow<DomainResult<List<Category>>> = flow {
    val cached = cache.getCategories()
    if (cached != null) emit(DomainResultSuccess(cached))
    try {
        val fresh = api.getCategories()?.data?.categories?.map { it.toDomain() }.orEmpty()
        cache.setCategories(fresh)
        emit(DomainResultSuccess(fresh))
    } catch (e: Exception) {
        emit(DomainResultError(code = 0, message = e.message ?: "Failed"))
    }
}
```

- **Always** `Flow<DomainResult<T>>` for network methods — UI needs 3 states.
- **Always emit error** even if cached data was emitted — cache is for speed, not hiding failures.
- `suspend fun` for one-shot mutations (signIn, submit, validate).
- Repository maps DTOs → domain models. Never expose DTOs above data layer.

## Module Cache

Each module owns its keys via `*Cache.kt`:
```kotlin
class DashboardCache(private val prefs: AppPreferences) {
    suspend fun getBoardingBonuses(): String? = prefs.get("dash_boarding_cache")
    suspend fun setBoardingBonuses(json: String) = prefs.set("dash_boarding_cache", json)
}
```

- Key prefix convention: `dash_`, `shop_`, `pay_`, `acc_`, `login_`, `fst_`, `tcmpp_`.
- Each key string appears exactly once — in the Cache class that owns it.
- Generic API uses `stringPreferencesKey` only — booleans stored as `"true"/"false"`.
- Adding a new cache key **never requires a commons change**.

## Rules

- `Flow<DomainResult<T>>` for cache-first. `suspend fun` for one-shot.
- Never `Flow<T?>` for network methods.
- Repositories return `DomainResult<DomainModel>` — never a bespoke per-endpoint `Result` type. Encode HTTP semantics (e.g. 410) in `DomainResultError.code`.
- New/changed ApiService methods return `ApiResult<T>` (status-aware) when the caller must distinguish HTTP codes; map to `DomainResult` in the repository. Legacy `T?` stays for untouched code.
- Interface in `domain/repository/`, implementation in `data/repository/`.
- DTOs in `data/remote/dto/`. Never import in `domain/` or `app/`.

## ServiceType / UserType (Identity Enums)

Single source of truth: `com.telkomsel.mytelkomsel.features.commons.model.ServiceType`

```kotlin
enum class ServiceType(val apiValue: String) {
    MOBILE("mobile"), INDIHOME("indihome"), ORBIT("orbit");
    companion object {
        fun parse(value: String?): ServiceType?         // null-safe, handles all legacy aliases
        fun parseOrDefault(value: String?): ServiceType // never null, defaults to MOBILE
    }
}
```

Related enums (same package):
- `UserType` — PREPAID, POSTPAID, CORPORATE, CORPORATE_PERSONAL_PAID, INDIHOME, ORBIT
- `ServiceGroup` — derived from ServiceType + UserType: SIMPATI, HALO, INDIHOME, ORBIT, OTHER
- `BillingProductType` — payment-specific: POSTPAID, INDIHOME, FMC_BILL

### Rules

| Layer | Type used | Example |
|---|---|---|
| Domain / App | Typed enum `ServiceType` | `when (serviceType) { ServiceType.ORBIT -> ... }` |
| Data (DTO fields) | `String?` from API | `@SerialName("service_type") val serviceType: String? = null` |
| Data → Domain mapper | `ServiceType.parse(dto.serviceType)` | Parse at repository boundary |
| Prefs / Cache (store) | `ServiceType.MOBILE.apiValue` | Always lowercase string |
| Prefs / Cache (read) | `ServiceType.parse(prefs.get(...))` | Null-safe parsing |
| API request params | `ServiceType.MOBILE.apiValue` | `tab = ServiceType.MOBILE.apiValue` |
| Route defaults | `ServiceType.MOBILE.apiValue` or `.name` | Depends on API expectation |

### Anti-Patterns

| ❌ Don't | ✅ Do |
|---|---|
| `serviceType == "orbit"` | `ServiceType.parse(serviceType) == ServiceType.ORBIT` |
| `tab.contains("indihome")` | `ServiceType.parse(tab) == ServiceType.INDIHOME` |
| `serviceType = "MOBILE"` | `serviceType = ServiceType.MOBILE.name` |
| `val ORBIT = "orbit"` (hardcoded const) | `val ORBIT = ServiceType.ORBIT.apiValue` |
| Local `enum ServiceType { POSTPAID, INDIHOME }` | Use commons `BillingProductType` for billing |
| `custType.equals("Orbit", true)` | `ServiceType.parse(custType) == ServiceType.ORBIT` |
| `if (brand.contains("orbit"))` | `ServiceType.parse(brand) == ServiceType.ORBIT` |
