---
inclusion: fileMatch
fileMatch:
  - "**/app/**"
  - "**/*Screen*"
  - "**/*Section*"
  - "**/*View*"
  - "**/*Sheet*"
  - "**/*Wcms*"
---
# UI Rules

## Naming Hierarchy

| Level | Suffix | Can Pass VM? | Example |
|---|---|---|---|
| Page | `Screen` | Via Container | `ShopScreen` |
| Page | `Sheet` | Via Container | `AliasEditSheet` |
| Page | `Dialog` | Via Container | `PinConfirmDialog` |
| Organism | `Section` | Via Container | `TelcoSection` |
| Molecule | `View` | ❌ Never | `OfferCardView` |
| Atom | `DX*` | ❌ Never | `DXButton` |

- `*View` must NEVER accept a ViewModel. All data via params.
- Function name matches file name: `ShopScreen.kt` → `fun ShopScreen(state, dispatch)`.

## WCMS (i18n)

```kotlin
// Co-located Wcms file
object ShopWcms {
    val HEADER_TITLE = "shop_revamp_header_text" to Res.string.shop_header_title
}
// In composable:
Text(wcms(ShopWcms.HEADER_TITLE))
```

- Never hardcode user-visible strings. Always `wcms(Pair)`.
- Never resolve WCMS in ViewModel. Resolve in composables only.
- Assets: `wcmsAsset(Pair<String, DrawableResource>)`.

## Recomposition Control

- All MVI State annotated `@Immutable`. Domain models in State also `@Immutable`.
- `LazyColumn`/`LazyRow` items always have `key { it.id }`.
- No lambda allocation inside `items {}` — hoist with `remember`.
- Shimmer/animated backgrounds use `drawBehind` (not `Modifier.background()`).
- Use `derivedStateOf` for computed data.

## Shared Components

If 2+ modules need the same composable, extract to `commons/app/`. Key reusables:

| Component | Level |
|---|---|
| `MsisdnSwitcherView` | View |
| `NoConnectionDialog` | Dialog |
| `CoachmarkOverlayView` | View |
| `PlatformWebView` | View |
| `RoundedTopCardView` | View |

## LocalTopAppBar (Ownership Pattern)

Sub-screens set toolbar config via `LocalTopAppBar.current`. Use the **ownership pattern** to prevent race conditions where a disposing screen blanks the next screen's toolbar.

```kotlin
val topAppBarState = LocalTopAppBar.current
val ownerKey = remember { Any() }

topAppBarState.setConfig(
    TopAppBarConfig.Standard(title = "My Screen", onBack = { ... }),
    ownerKey,
)
DisposableEffect(Unit) {
    onDispose { topAppBarState.clearIfOwner(ownerKey) }
}
```

**Why:** During navigation transitions, the old screen's `onDispose` can fire AFTER the new screen sets its config. Without ownership, this blanks the toolbar. `clearIfOwner` only clears if the disposing screen is still the current owner.

| ❌ Don't | ✅ Do |
|---|---|
| `topAppBarState.config = TopAppBarConfig.Standard(...)` | `topAppBarState.setConfig(config, ownerKey)` |
| `onDispose { topAppBarState.config = TopAppBarConfig.None }` | `onDispose { topAppBarState.clearIfOwner(ownerKey) }` |

**Note:** Direct `config =` setter still works for backward compat but does NOT track ownership. Migrate screens incrementally.

## Colors

Never scatter raw `Color(0xFF…)` literals in feature code. Resolve in priority order:

1. **DX tokens (preferred)** — use `DX.Color.*` when an exact token exists. Check `docs/DX.md` /
   `TelkomselDX/theme/Color.kt` first.
   ```kotlin
   color = DX.Color.text.primary
   background = DX.Color.background.navy   // #001A41
   ```
2. **`CommonsColor` (non-DX / legacy parity values)** — shared **color palette** in
   `:features:commons` (`commons/util/CommonsColor.kt`), named by color (`Mist`, `Pearl`, `Ash`…),
   NOT by usage. Add a named, documented entry; never inline the literal. Reusable by every module.
   Migrate to DX when a matching token appears.
   ```kotlin
   color = CommonsColor.Ash   // #9C9C9C (no DX equivalent)
   ```
3. **API/WCMS hex strings** — parse via the shared `commons/util/ColorUtils.kt`; never re-implement.
   ```kotlin
   val bg = parseHexColor(dto.bgColor) ?: DX.Color.background.red  // #RRGGBB/#AARRGGBB/#RRGGBBAA
   val fg = dto.textColor.toColor()                                // non-null (falls back to red)
   ```

| ❌ Don't | ✅ Do |
|---|---|
| `Color(0xFFED0226)` inline | `DX.Color.background.red` |
| `Color(0xFF9C9C9C)` inline (no DX token) | `CommonsColor.Ash` |
| Per-feature `private fun parseHexColor` | `commons.util.parseHexColor` |
| Per-file `private val SomeColor = Color(0xFF…)` | Add to `CommonsColor` |

## Rules

- Every composable file must have a suffix: `Screen`, `Sheet`, `Dialog`, `Section`, `View`, or `DX*`.
- `*View` composables should be previewable — design params for `@Preview`.
- DX atoms are never wrapped — use directly.
- All new screens MUST use the TopAppBar ownership pattern (`setConfig` + `clearIfOwner`).

## Bottom Sheets

Feature code MUST NOT call raw `androidx.compose.material3.ModalBottomSheet`. All bottom sheets
are **self-contained**, mirroring the `SectionContainer` pattern: the container lives INSIDE the
`*Sheet` file, not in NavGraph / a sheet host. The caller only toggles a visibility flag.

### Status — alignment COMPLETE

The mass migration is **done and compile-verified green across all modules**:

- `AppBottomSheet` (`:features:core`) is the **ONLY** caller of
  `androidx.compose.material3.ModalBottomSheet` in `features/`. Every raw `ModalBottomSheet` caller
  and every direct `AppBottomSheet` feature caller has been migrated.
- **All** sheets route through `SheetContainer` (VM-owning) or `StatelessSheetContainer` (VM-less).
  Feature code never touches `ModalBottomSheet` or `AppBottomSheet` directly.
- **Zero embedded-body exceptions remain.** The former "body embedded inside another sheet" carve-out
  (ForYou / Prestige) was converted to `StatelessSheetContainer`.

> Caveat (accepted, out-of-scope): a programmatic `visible = false` that is NOT triggered by a user
> gesture may hard-cut instead of animating out. This is an M3 `ModalBottomSheet` limitation — the
> exit animation is driven by the sheet's own gesture/hide path, which a blunt state flip bypasses.

### Two layers, composed

| Layer | Component | Owns |
|---|---|---|
| Wiring | `SheetContainer` / `StatelessSheetContainer` | VM inject (VM variant only), Navigator, `DXScreen` (prod analytics), `TrackView(name)`, effect collection, and the sheet chrome |
| Chrome | `AppBottomSheet` (`:features:core`) | The `ModalBottomSheet`, drag handle, shape, container color, scrim / drag-dismiss behavior |

The containers render `AppBottomSheet` internally — feature code never touches either directly.

### Which pattern to use

| Sheet kind | Pattern |
|---|---|
| Owns a ViewModel | `fun XxxSheet(onDismiss) { SheetContainer<VM, S, E>(name, onDismiss) { state, dispatch -> XxxContentView(state, dispatch) } }` |
| Stateless / uses parent state or params | `fun XxxSheet(...args, onDismiss) { StatelessSheetContainer(name, onDismiss) { XxxContentView(...) } }` |

```kotlin
// CartSheet.kt — self-contained, mirrors TelcoSection
@Composable
fun CartSheet(onDismiss: () -> Unit) {
    SheetContainer<CartViewModel, CartContract.State, CartContract.Event>(
        name = "CartSheet",
        onDismiss = onDismiss,
    ) { state, dispatch ->
        CartContentView(state, dispatch)   // pure content — no ModalBottomSheet, no TrackView
    }
}

// Caller (screen) — owns show/hide only
if (showCart) CartSheet(onDismiss = { showCart = false })
```

### `visible` param + always-composed sheets

Both containers accept `visible: Boolean = true`. When a sheet was **always composed** (not gated by
`if (show)` at the call site) and toggled by a flag, forward that flag via `visible =` instead:

```kotlin
// OtpErrorSheet.kt — always composed, toggled via visible=
@Composable
fun OtpErrorSheet(visible: Boolean, ..., onDismiss: () -> Unit) {
    StatelessSheetContainer(name = "OtpErrorSheet", visible = visible, onDismiss = onDismiss) {
        OtpErrorContentView(...)   // stays composed so the M3 slide-out plays
    }
}
```

How the gate works (identical in both containers):

- `DXScreen` (prod `screen_view`) + `TrackView(name)` (perf node) emit **ONLY** when `visible == true`.
  A hidden, always-composed sheet fires **zero** phantom analytics / perf nodes for a sheet the user
  never saw.
- `AppBottomSheet` is rendered in **BOTH** branches (never gated out by the `visible` check) so it
  keeps ownership of its own M3 hide/exit animation. When hidden it is rendered **without** the
  analytics wrappers and self-early-returns on `visible = false`.
- The container passes `name = null` to `AppBottomSheet` in both branches so `TrackView` is never
  duplicated.
- Sheets that had a pre-migration visibility toggle **restore** it via `visible =` (it is not
  defaulted away).

### Optional chrome params

Both containers forward optional chrome params to the internal `AppBottomSheet`. Defaults preserve
existing behavior — omit them unless you need a specific look:

| Param | Default | Effect |
|---|---|---|
| `containerColor` | `DX.Color.background.white` | Sheet background color |
| `showDragHandle` | `true` | `false` hides the drag handle |
| `sheetMaxWidth` | `null` | `null` uses M3 default (`BottomSheetDefaults.SheetMaxWidth`) |
| `heightFraction` | `null` | Fixed height fraction `0f..1f` for tall/scrollable sheets |
| `dismissOnOutsideClick` | `true` | `false` = non-dismissible by gesture |

> In `SheetContainer`, chrome params apply only in self-contained mode (`onDismiss != null`); the
> legacy content-only branch (`onDismiss == null`) ignores them because the content supplies its
> own sheet.

### Rules

- Content composables are pure: NO `ModalBottomSheet`, NO `TrackView`, NO `DXScreen`. The container
  owns `TrackView(name)` + `DXScreen`. When the container renders `AppBottomSheet` internally it passes
  `name = null` so tracking is not duplicated.
- The **caller owns the show/hide flag** (`if (show) XxxSheet(onDismiss = { show = false })`). The
  `*Sheet` owns VM, chrome, and content. For always-composed sheets, forward the flag via `visible =`.
- Dismiss-gesture policy via `dismissOnOutsideClick` (default `true`). `false` = non-dismissible by
  gesture: scrim tap / drag-down RE-EXPAND the sheet (they do not dismiss); only an explicit in-sheet
  action calls `onDismiss`. Use for gates (consent, forced explore). Never leave a non-dismissible
  sheet able to drag-dismiss with an empty `onDismiss` — the M3 scrim orphans and blocks the screen.
- File/function naming follows the `*Sheet` suffix rule; the pure body may be `*ContentView`.

### Adoption

The migration is complete — `SheetContainer` / `StatelessSheetContainer` / `AppBottomSheet` in
`:features:core` are the only sanctioned way to build a sheet. Every NEW sheet MUST use a container.
Never introduce a raw `ModalBottomSheet` or a direct `AppBottomSheet` feature caller.

## Dialogs

The dialog counterpart of Bottom Sheets. Feature code MUST NOT call raw
`androidx.compose.material3.BasicAlertDialog` / `AlertDialog` / `Dialog`. All centered dialogs are
**self-contained**, mirroring the sheet pattern: the container lives INSIDE the `*Dialog` file. The
caller only toggles a visibility flag.

Use a dialog (not a sheet) for short, focused, blocking decisions — confirmations, forced updates,
single-action prompts. Use a sheet for richer/scrollable content or list selection.

### Status — alignment COMPLETE

The mass migration is **done and compile-verified green on the Android production path**:

- `AppDialog` (`:features:core`) is the **ONLY** caller of raw
  `androidx.compose.material3.BasicAlertDialog` / `AlertDialog` / `androidx.compose.ui.window.Dialog`
  in `features/`. Every raw-dialog caller and every direct `AppDialog` feature caller has been
  migrated.
- **All** centered dialogs route through `DialogContainer` (VM-owning) or `StatelessDialogContainer`
  (VM-less). Feature code never touches a raw dialog primitive or `AppDialog` directly.
- Migrated + renamed to the `*Dialog` suffix: shop `OtpVerificationSheet` → `OtpVerificationDialog`,
  `PaymentScreen` inline `Dialog` → `PaymentDetailDialog`, plus new `AutoGiftUnsubscribeDialog`,
  `ByuInfoDialog`, `GiftLimitDialog`, `TransferQuotaInfoDialog`; account `StatusSheetView` →
  `StatusDialog`; commons `NoConnectionSheet` → `NoConnectionDialog`.
- Forced gates (`dismissOnOutsideClick = false`): `OtpVerificationDialog`, `NoConnectionDialog`. All
  other dialogs use the default `true`.
- Content composables are pure — no `TrackView`, no `DXScreen`, no raw dialog/sheet chrome. The
  container owns `TrackView(name)` + `DXScreen(eventCategory = "dialog_view")` and passes
  `name = null` to `AppDialog` so tracking is not duplicated.
- Cross-platform host imports for `NoConnectionDialog` corrected: Android `MainActivity`, iOS
  `Main.kt`, desktop `main.kt` — the iOS/desktop imports were **stale** (`...features.core.app`) and
  now point at `...features.commons.app`.

> Caveat (accepted, out-of-scope — pre-existing, NOT a regression from this migration):
> `NoConnectionDialog` renders correctly but is display-gated by `NoConnectionState.show()`. A producer
> exists only on Android (`ObserveConnectivity` in `:app` androidMain, wired in `MainActivity`, calls
> `show()` on network loss). On iOS a producer exists but is **not invoked** by the host, and on
> desktop there is **no** producer at all; additionally **no HTTP-error interceptor** calls `show()`
> on any platform. See `features/commons/docs/spec/dialogcontainer-alignment.md` → "Known pre-existing
> gap" for the suggested follow-up. Desktop target also has pre-existing unrelated breakage
> (tcmpp/login) untouched by this migration.

### Two layers, composed

| Layer | Component | Owns |
|---|---|---|
| Wiring | `DialogContainer` / `StatelessDialogContainer` | VM inject (VM variant only), Navigator, `DXScreen` (prod analytics, `eventCategory = "dialog_view"`), `TrackView(name)`, effect collection, and the dialog chrome |
| Chrome | `AppDialog` (`:features:core`) | The `BasicAlertDialog`, rounded `Surface`, container color, content padding, scrim / back-dismiss behavior |

The containers render `AppDialog` internally — feature code never touches either directly. This
mirrors `SheetContainer`/`StatelessSheetContainer` → `AppBottomSheet` exactly.

### Which pattern to use

| Dialog kind | Pattern |
|---|---|
| Owns a ViewModel | `fun XxxDialog(onDismiss) { DialogContainer<VM, S, E>(name, onDismiss) { state, dispatch -> XxxContentView(state, dispatch) } }` |
| Stateless / uses parent state or params | `fun XxxDialog(...args, onDismiss) { StatelessDialogContainer(name, onDismiss) { XxxContentView(...) } }` |

```kotlin
// PinConfirmDialog.kt — self-contained, mirrors CartSheet
@Composable
fun PinConfirmDialog(onDismiss: () -> Unit) {
    DialogContainer<PinConfirmViewModel, PinConfirmContract.State, PinConfirmContract.Event>(
        name = "PinConfirmDialog",
        onDismiss = onDismiss,
    ) { state, dispatch ->
        PinConfirmContentView(state, dispatch)   // pure content — no BasicAlertDialog, no TrackView
    }
}

// Caller (screen) — owns show/hide only
if (showPin) PinConfirmDialog(onDismiss = { showPin = false })
```

### Optional chrome params

Both containers forward optional chrome params to the internal `AppDialog`. Defaults preserve the
standard look — omit them unless you need a specific one:

| Param | Default | Effect |
|---|---|---|
| `containerColor` | `DX.Color.background.white` | Dialog background color |
| `contentPadding` | `24.dp` | Inner padding around content |
| `dialogMaxWidth` | `null` | `null` sizes to content within the standard dialog window |
| `dismissOnOutsideClick` | `true` | `false` = non-dismissible by scrim tap / back |

### Rules

- Content composables are pure: NO `BasicAlertDialog`, NO `TrackView`, NO `DXScreen`. The container
  owns `TrackView(name)` + `DXScreen`; it passes `name = null` to `AppDialog` so tracking is not
  duplicated.
- The **caller owns the show/hide flag** (`if (show) XxxDialog(onDismiss = { show = false })`). The
  `*Dialog` owns VM, chrome, and content.
- Dismiss-gesture policy via `dismissOnOutsideClick` (default `true`). `false` = non-dismissible by
  scrim tap / back — use for gates (forced update, mandatory confirm); only an explicit in-dialog
  action calls `onDismiss`.
- Unlike sheets, dialogs have no manual slide-out: when `visible = false` the dialog is simply not
  composed (the platform window handles the fade). There is no always-composed `visible =` exit
  animation requirement.
- File/function naming follows the `*Dialog` suffix rule; the pure body may be `*ContentView`.

### Adoption

The migration is complete — `DialogContainer` / `StatelessDialogContainer` / `AppDialog` in
`:features:core` are the only sanctioned way to build a dialog. Every NEW dialog MUST use a
container. Never introduce a raw `BasicAlertDialog` / `AlertDialog` / `Dialog` or a direct
`AppDialog` feature caller.
