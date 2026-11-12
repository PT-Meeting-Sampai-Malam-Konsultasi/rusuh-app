---
inclusion: fileMatch
fileMatch:
  - "**/*Test*"
  - "**/*test*"
  - "**/proguard*"
---
# Quality & Operations

## Testing

| Level | Tool | Where |
|---|---|---|
| Unit | kotlin-test + coroutines-test | `commonTest/` |
| Integration | koin-test | `commonTest/` |

- Every new ViewModel MUST have `*Test.kt`.
- Use `Fake*` classes, not mockk — keeps tests KMP-compatible.
- Run: `./gradlew :features:{module}:testDevelopmentDebugUnitTest`

```kotlin
class ShopViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(testDispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test fun load_emitsCategories() = runTest {
        val vm = ShopViewModel(FakeGetCategoriesUseCase())
        vm.setEvent(Event.Load)
        advanceUntilIdle()
        assertEquals(ScreenStatus.Success, vm.uiState.value.status)
    }
}
```

## Performance

- Cold start budget: <1s splash on mid-range device.
- Cache-first everywhere — show cached, refresh in background.
- Parallel section loading — each VM loads independently.
- No DataStore reads before first frame.
- Shimmer uses `drawBehind` — zero recompositions per frame.

## Security

- No secrets in source code — all via `local.properties` → BuildKonfig.
- No `println`/`Log.d` with tokens or user data.
- `coerceInputValues = true` on all Json configs.
- Tokens stored in DataStore, never logged.

## Quality Gates (Pre-Commit)

```
git commit → spotless → detekt → unit-test → kover → lint
```

All 6 gates must pass. Never `--no-verify`.

## kover + `logD {}` diagnostic lambdas (recurring gotcha)

Diagnostic logging via `logD(TAG) { "…" }` (core `Logger.kt`) is a **no-op unless Koin holds
`named("isDebug") = true`**. In unit tests Koin usually has no `isDebug`, so the **message-lambda
body lines never execute** and kover marks them **missed** — silently dropping a module below the
99% line-rate rule. This is the usual reason a freshly-added file with a couple of `logD {}` calls
tips `koverVerify` just under threshold, even though every behavioral line is tested.

When `koverVerify` fails and the missed lines are `logD {}` (or `logW`/`logE`) message lambdas:

1. **Check the kover XML** (`build/reports/kover/report.xml`) for `mi="…"` lines — if they land on
   `logD { … }` bodies, this is the cause.
2. **Plant `isDebug=true` in the test's Koin** so the lambdas execute and get covered:
   ```kotlin
   @BeforeTest fun setup() { startKoin { modules(module { single(named("isDebug")) { true } }) } }
   @AfterTest  fun teardown() { stopKoin() }
   ```
   Pair `startKoin`/`stopKoin` per test class; never leave Koin started (pollutes other tests).
3. This is the correct fix — it genuinely exercises the logging. Do NOT delete the logs, do NOT
   exclude the class from kover, do NOT drop the threshold, and do NOT convert to a block body.

Separately, if a **single-expression `suspend` delegator** (`override suspend fun call(...) = repo.x()`)
is genuinely uncovered, first add a real test that calls it (the common case). Only if a passing test
demonstrably exercises it yet kover still won't credit it (rare instrumentation edge) use a **named
class exclude** in the root `guardrails { coverage { excludeClasses += … } }` list with a comment —
never a threshold drop or `@Suppress`.

Rule of thumb: one diagnosis pass — read the kover XML, identify the missed lines, apply the matching
fix (logD → isDebug test setup; untested logic → real test). Don't thrash across multiple passes.
