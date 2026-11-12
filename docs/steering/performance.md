---
inclusion: always
---
# Performance

## Scroll Performance (Critical)

### forwardVerticalScroll is NO-OP on Android

`Modifier.forwardVerticalScroll(lazyListState)` is a **no-op on Android/iOS**. Only Desktop uses it (mouse wheel events lack touch disambiguation).

**DO NOT** add custom `NestedScrollConnection` for vertical scroll forwarding on Android. Compose Foundation 1.5+ handles HorizontalPager inside LazyColumn natively.

| Platform | Implementation | Reason |
|---|---|---|
| Desktop | `pointerInput` intercepting `PointerEventType.Scroll` | Mouse wheel has no touch disambiguation |
| Android | No-op (`= this`) | Compose nested scroll handles it natively since Foundation 1.5+ |
| iOS | No-op (`= this`) | UIKit touch disambiguation handles it natively |

**Why:** Custom `NestedScrollConnection` with `dispatchRawDelta` causes double-processing of scroll deltas — the manual scroll AND the native nested scroll chain both process the same event, causing velocity discontinuities and jank. See `docs/specs/dashboard-scroll-optimization.md`.

### Rules

- Never `dispatchRawDelta` inside `onPreScroll` for vertical forwarding on Android.
- Never add `NestedScrollConnection` to "fix" HorizontalPager scroll inside LazyColumn.
- The `forwardVerticalScroll` modifier MUST remain in section code (for desktop). Just don't modify the Android actual.
- If a pager section has scroll issues on Android, the bug is elsewhere (not in scroll forwarding).

## Cold Start

- Budget: <1s splash on mid-range device.
- Skeleton-first rendering: `isGuest == null` renders default section list immediately from Frame 1.
- Section VMs load lazily via LazyColumn virtualization — only composed when entering viewport.
- No DataStore reads before first frame.

## Compose Stability

- All MVI State annotated `@Immutable`. Domain models in State also `@Immutable`.
- `LazyColumn`/`LazyRow` items always have `key { it.id }`.
- No lambda allocation inside `items {}` — hoist with `remember`.
- Shimmer/animated backgrounds use `drawBehind` (not `Modifier.background()`).
- Use `derivedStateOf` for computed data from scroll state.

## Section Loading

- Cache-first everywhere — show cached, refresh in background.
- Parallel section loading — each SectionContainer VM loads independently.
- VMs fire `init { load() }` — lazy by LazyColumn composition, NOT all at once.
- Avoid `BoxWithConstraints` — use `LocalConfiguration.current.screenWidthDp` for tablet detection.

## Measurement

```bash
# Quick jank check (after authenticated scroll test):
adb shell dumpsys gfxinfo com.telkomsel.mytelkomsel framestats | grep -A8 "Janky frames"

# Target: <3% janky frames, 90th pct <16ms
```

## Deferred Optimizations

| Item | Status | Blocker |
|---|---|---|
| `beyondBoundsItemCount = 2` | Deferred | Not available in Compose Multiplatform 1.10.2 |
| Replace `BoxWithConstraints` with `LocalConfiguration` | Deferred | P2 priority, low impact |
| Remove `trackPerformance` modifier (debug overhead) | Deferred | Requires updating ~10 files across modules |
