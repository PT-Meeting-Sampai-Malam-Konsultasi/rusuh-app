---
inclusion: always
---
# Quick Reference

## Anti-Patterns

| ❌ Don't | ✅ Do |
|---|---|
| Manual `koinViewModel + LaunchedEffect + TrackView` in new screens | `ScreenContainer` / `SectionContainer` |
| `Container<VM, State, Effect, Event>` (4 type params) | `Container<VM, State, Event>` (3 params — no Effect) |
| `MVIViewModel<Event, State, Effect>` (3 type params) | `MVIViewModel<Event, State>` (2 params) |
| `sealed class Effect` with ShowError/ShowToast/ShowSnackbar | `setEffect { UiEffect.Error/Toast/Snackbar(...) }` |
| `sealed class Effect` with Navigate/NavigateBack | `setEffect { NavEffect.To/Back/Deeplink(...) }` |
| `effectRouter` that just calls `ToastManager.showToast` | Remove — `UiEffect` auto-dispatches |
| `effectRouter` that just calls `navigator.navigateDeeplink` | Remove — use `NavEffect.Deeplink` in VM |
| FQN inline (`com.telkomsel.x.y.Z`) | `import` at top |
| `Flow<T?>` for network repo methods | `Flow<DomainResult<T>>` |
| Bespoke per-endpoint `sealed Result { Success/Error/Expired }` | Reuse `DomainResult`; encode HTTP codes (410 etc.) in `DomainResultError.code` |
| ApiService returns `T?` when caller must distinguish HTTP codes | Return `ApiResult<T>` (status-aware `tryGetWithStatus`), map to `DomainResult` in repo |
| Overloading `ApiResponse` with HTTP status/error handling | Keep `ApiResponse` = body envelope; use `ApiResult` for transport outcome |
| Hardcode strings in composables | `wcms(Pair)` |
| **Any string literal used in logic** (`"Supreme"`, `"product_key_"`, `"dashboardCacheTTL"`, route/type/status strings, etc.) | **Forbidden — always extract to a named `const val`.** User-visible text → `wcms(Pair)`; RC keys → `{Feature}RcKeys`; storage keys → `*Cache`; everything else → file-level or `companion object` `const val`. No raw string literals inline in code. |
| **Magic numbers** in logic (`delay(3000)`, `if (code == 410)`, `take(5)`, `retry(3)`, `0.7f` alpha, `24 * 60 * 60 * 1000`) | **Extract to a named constant** whose name says the meaning + unit: `SplashDelayMs`, `HTTP_GONE`, `MAX_RECENT_SEARCHES`, `CACHE_TTL_MS` (naming by position per the constant-naming row). Prefer `Duration` for time (`3.seconds`, `1.days`). UI dimensions → `DX.Spacing.*` / DX tokens first, then a file-level `private val CardRadius = 12.dp`. Allowed inline: `-1`, `0`, `1`, `2` in obvious arithmetic/indexing, `100` for percent math, and values passed as **named arguments** where the param name already explains them. Never `@Suppress("MagicNumber")`. |
| Import DTOs in domain/app layer | Map to domain models in repository |
| `companion object` at the top / middle of a class | Place it at the **bottom** of the class body (after all functions/properties). Kotlin style + codebase convention. Exception: a factory `companion` that is the class's primary entry point may sit near the top. |
| Constant naming mixed by position (`private const val KEY_MOBILE = …` at file top, `const val tag = …` in companion) | **Name by where it is declared.** **Top-level (file-level, above the class)** → Kotlin/Compose recommended **PascalCase**: `private const val VolumeMuted = 0f`, `private val ScreenBackground = Color(…)` (detekt `TopLevelPropertyNaming` flags SCREAMING_SNAKE here). **`companion object` at the bottom of the class** → **capitals**: single word `TAG`, multi-word **SCREAMING_SNAKE_CASE** `EP_CATEGORIES`, `CACHE_TTL_MS`. Never `@Suppress("TopLevelPropertyNaming")`; rename instead. |
| `try { … } catch (_: Exception) { fallback }` / `catch (e: Exception)` where the exception is never referenced — **in any code** (ViewModel, UseCase, Repository, Cache, mapper, util, composable) | **Use `runCatching`**: `runCatching { x.toInt() }.getOrNull()` / `.getOrDefault(d)` / `.getOrElse { d }` / `.onFailure { … }`. Keep `try/catch` only when the exception object is actually used (logged, mapped, rethrown) or when catching a specific exception type. **Suspend caveat:** `runCatching` also swallows `CancellationException`; inside `suspend` code rethrow it: `.onFailure { if (it is CancellationException) throw it }`. |
| Block body for a single-expression function (`fun x(): T { return y }`) | **Always use an expression body** (`fun x(): T = y`). Codebase + detekt convention. Applies to `override suspend fun call(...) = repo.x()`, mappers, delegators, etc. |
| Function / constructor / composable with **more than 7 parameters** | **Max 7 params** (Sonar `kotlin:S107`). Group related values into a data class: a `Params` for UseCases, a UI model / state slice for `*View`, an `Actions` holder (data class of lambdas) for callbacks. `modifier` and defaulted params count. Note: local detekt `LongParameterList` sits at 15, so the pre-commit hook won't catch 8–15 — Sonar will. Never `@Suppress("LongParameterList")`. |
| Function with **complexity over 15** (deep `if`/`when`/loop nesting, long `&&`/`\|\|` chains, nested lambdas with branches) | **Max 15** for both cognitive complexity (Sonar `kotlin:S3776`) and cyclomatic complexity (detekt `CyclomaticComplexMethod`). Fix by splitting: extract each `when` branch body or validation step into a private function, use early returns / guard clauses instead of nesting, move mapping logic into mappers/UseCases, and split big composables into smaller `*View`s. Never `@Suppress("CyclomaticComplexMethod")` / `CognitiveComplexMethod`. |
| `@Suppress("MaxLineLength")` (file-level or inline) to silence a long line | **Forbidden.** Fix the line: break the expression across lines, extract a local `val`, or wrap arguments one-per-line. Never suppress the line-length rule. |
| Redundant inline comment that restates a self-explanatory name (`// Quick menu` above `EP_QUICK_MENU`, `// loop` before a `for`) | Omit it. Comments should explain **why** (intent, gotcha, backend quirk), not **what** the code already says. Keep comments for non-obvious logic only. |
| `collectAsStateWithLifecycle()` on `uiState` | `vm.uiState.value` (already Compose State) |
| Inject Repository/Prefs in ViewModel | Wrap in UseCase — VMs inject UseCases only |
| `Dispatchers.IO` in VM `scope.launch {}` | Plain `scope.launch {}` |
| `isLoading`/`isError` booleans in State | Single `status: ScreenStatus` enum (`isLoadingMore` for paging is acceptable) |
| `Modifier.trackPerformance("XSection")` on Section | Container handles TrackView |
| `LazyColumn` items without `key` | `items(list, key = { it.id })` |
| `git add .` | Stage specific files |
| No README in feature module | Every module has README.md |
| `serviceType == "orbit"` or `contains("indihome")` | `ServiceType.parse(x) == ServiceType.ORBIT` |
| `serviceType = "MOBILE"` (hardcoded string) | `serviceType = ServiceType.MOBILE.name` |
| `val ORBIT = "orbit"` (local const) | `val ORBIT = ServiceType.ORBIT.apiValue` |
| Local `enum ServiceType` in feature module | Import `commons.model.ServiceType` |
| `custType.equals("Orbit", true)` | `ServiceType.parse(custType) == ServiceType.ORBIT` |

## Migration Checklist (New Feature Module)

1. Create module — `features/{name}/build.gradle.kts` depending on `:features:commons`
2. Register in `settings.gradle.kts` and `:app` dependencies
3. Create Koin module — `di/{Name}Module.kt`, add to `appModule` includes
4. Data layer — ApiService + DTOs + Repository (cache-first)
5. Domain models — map DTOs → clean domain models
6. Presentation — Contract + ViewModel + Content composable
7. Navigation — Routes + NavGraph with `ScreenContainer`
8. WCMS keys — co-located `*Wcms.kt`
9. Documentation — Module `README.md` + per-screen `README.md`
10. Verify — compile, install, test on device
11. (Optional) DFM shell — if on-demand delivery needed:
    - Create `dfm/{name}/` with `EntryPoint.kt` + `Activity.kt`
    - Activity uses `ScaffoldContainer` + local Navigator
    - Add intent-filters (`${deeplinkCustomScheme}` + `https://${deeplinkWebHost}`)
    - Add `:app` desktopMain dependency on `:features:{name}` (Desktop bundles directly)
