---
inclusion: always
---
# MVI + Container Pattern

## Container Pattern (Primary)

All NavGraph routes use `ScreenContainer`. All LazyColumn sections with own VM use `SectionContainer`. Bottom sheets with own VM use `SheetContainer`. Centered dialogs with own VM use `DialogContainer`. These bake in: VM injection, Navigator injection, DXScreen (production analytics), TrackView (debug perf), effect collection.

```kotlin
// Screen (NavGraph route) — 3 type params: <VM, State, Event>
composable<ShopRoute> {
    ScreenContainer<ShopViewModel, ShopContract.State, ShopContract.Event>(name = "ShopScreen") { state, dispatch ->
        ShopScreen(state, dispatch)
    }
}

// Sheet (bottom sheet with own VM)
composable<AliasEditRoute> {
    SheetContainer<AliasEditViewModel, AliasContract.State, AliasContract.Event>(name = "AliasEditSheet") { state, dispatch ->
        AliasEditSheet(state, dispatch)
    }
}

// Dialog (centered dialog with own VM) — self-contained, mirrors SheetContainer
@Composable
fun PinConfirmDialog(onDismiss: () -> Unit) {
    DialogContainer<PinConfirmViewModel, PinConfirmContract.State, PinConfirmContract.Event>(
        name = "PinConfirmDialog",
        onDismiss = onDismiss,
    ) { state, dispatch ->
        PinConfirmContentView(state, dispatch)   // pure content — no BasicAlertDialog, no TrackView
    }
}

// Section (self-contained, parent doesn't know VM types)
item { TelcoSection() }
```

Sections own their Container internally — the parent just calls `TelcoSection()`:
```kotlin
// TelcoSection.kt
@Composable
fun TelcoSection() {
    SectionContainer<TelcoViewModel, TelcoContract.State, TelcoContract.Event>(name = "TelcoSection") { state, dispatch ->
        TelcoContentView(state.bonuses, onTab = { dispatch(Event.TabSelected(it)) })
    }
}
```

With parameters (e.g. AdsVM, FstBannerVM):
```kotlin
SectionContainer<AdsViewModel, AdsContract.State, AdsContract.Event>(
    name = "AdsHeroSection",
    key = "ads_hero",
    parameters = { parametersOf(concern, payload) },
) { state, dispatch -> ... }
```

For screen-specific effects (show sheet, apply filter), add `effectRouter`:
```kotlin
ScreenContainer<VM, State, Event>(name = "X", effectRouter = { effect, _ ->
    when (effect) {
        is Effect.ApplyFilter -> onApplyFilter(effect.filters)
        else -> {}
    }
}) { state, dispatch -> ... }
```

## Effect Dispatch Chain

Containers auto-dispatch effects in this priority:

```
1. is NavEffect  → effect.navigate(navigator)   // navigation
2. is UiEffect   → effect.dispatch()            // toast, error, snackbar, browser
3. effectRouter   → custom handler              // screen-specific (rare)
```

No manual wiring needed for navigation or common UI feedback.

## NavEffect (shared navigation effects)

All navigation is done via `NavEffect` from commons. Zero per-module declaration needed:

```kotlin
// In any ViewModel:
setEffect { NavEffect.To(PackageDetailRoute(bid = "123")) }
setEffect { NavEffect.Back }
setEffect { NavEffect.Deeplink("/shop/detail") }
setEffect { NavEffect.ToLogin(pendingRoute = "/account") }
setEffect { NavEffect.ShowMsisdnChanger() }
setEffect { NavEffect.ShowSignInSheet() }
setEffect { NavEffect.StatusBar(light = true) }
setEffect { NavEffect.Logout }
```

## UiEffect (shared UI feedback effects)

All toast/error/snackbar/success feedback uses `UiEffect` from commons. Zero per-module declaration needed:

```kotlin
// In any ViewModel:
setEffect { UiEffect.Toast("Copied!") }
setEffect { UiEffect.Error("Connection failed") }
setEffect { UiEffect.Error("Failed to load", title = "Network Error") }
setEffect { UiEffect.Snackbar("Purchase successful") }
setEffect { UiEffect.Success("Transfer complete") }
setEffect { UiEffect.OpenBrowser("https://telkomsel.com") }
```

`UiEffect` is self-dispatching — each subclass has its own `dispatch()` that uses `ToastManager` / `PlatformUrlOpener` internally. No DI, no interface, no effectRouter needed.

## MVI Contract

```kotlin
object ShopContract {
    sealed class Event : ViewEvent { ... }

    @Immutable
    data class State(val status: ScreenStatus = ScreenStatus.Loading, ...) : ViewState

    // ONLY screen-specific effects here (show sheet, apply filter, launch platform action)
    // Do NOT declare ShowError/ShowToast/ShowSnackbar — use UiEffect instead
    sealed class Effect : ViewSideEffect {
        data class ShowValiditySheet(val title: String, val desc: String) : Effect()
    }
}
```

- State is always `@Immutable` data class with sensible defaults.
- `ScreenStatus` enum: `Loading`, `Success`, `Empty`, `Error` — never boolean flags.
- Effects are one-shot. Navigation → `NavEffect`. UI feedback → `UiEffect`. Module effects → screen-specific only.
- If a Contract only has nav/UI effects, it doesn't need a `sealed class Effect` at all.

## ViewModel

```kotlin
class ShopViewModel(
    private val getCategoriesUseCase: GetCategoriesUseCase,
) : MVIViewModel<ShopContract.Event, ShopContract.State>() {
    override fun setInitialUiState() = ShopContract.State()
    override fun handleEvents(event: ShopContract.Event) {
        when (event) {
            is Event.Load -> loadCategories()
            is Event.ItemClicked -> setEffect { NavEffect.To(PackageDetailRoute(event.bid)) }
            is Event.Error -> setEffect { UiEffect.Error(event.message) }
        }
    }
}
```

- `MVIViewModel` takes **2 type params**: `<Event, State>`. Effects are always `ViewSideEffect` internally.
- **VMs inject UseCases ONLY** — never AppPreferences, Repository, or ApiService directly.
- Never use `Dispatchers.IO` in `scope.launch {}` — `MVIViewModel.scope` handles dispatching.
- Navigation: always `setEffect { NavEffect.X(...) }`. Never inject Navigator in VM.
- UI feedback: always `setEffect { UiEffect.X(...) }`. Never call ToastManager directly.

## UseCase Contract

```kotlin
interface CoreUseCase<in P : CoreUseCaseParams, out R> { suspend fun call(params: P): R }
suspend fun <R> CoreUseCase<NoParams, R>.call(): R = call(NoParams)
```

- Method is always `call(params)`. No `invoke()`, no `execute()`.
- Params: nested `data class Params(...) : CoreUseCaseParams`.
- Zero-arg: use `NoParams`. Callers write `useCase.call()`.
- One grouped file per module: `{Module}UseCases.kt`.

## Screen/Section Function

Every Screen/Section composable is a pure function matching its level:
```kotlin
@Composable
fun ShopScreen(state: ShopContract.State, dispatch: (ShopContract.Event) -> Unit)

@Composable
fun TelcoSection(state: TelcoContract.State, dispatch: (TelcoContract.Event) -> Unit)

@Composable
fun AliasEditSheet(state: AliasContract.State, dispatch: (AliasContract.Event) -> Unit)
```

- File and function share the same name: `ShopScreen.kt` → `fun ShopScreen(state, dispatch)`
- Views remain: `fun OfferCardView(data, callbacks)` — pure data, no dispatch.

## Rules

- Every new Screen MUST use `ScreenContainer`.
- Every new Section MUST use `SectionContainer`.
- Every new Sheet MUST use `SheetContainer`.
- Every new Dialog MUST use `DialogContainer` (VM-owning) or `StatelessDialogContainer` (VM-less). Feature code never calls raw `BasicAlertDialog` / `AlertDialog` / `Dialog` — the containers render `AppDialog` chrome internally.
- Container type params: `<VM, State, Event>` — 3 params only. Never pass Effect type.
- `MVIViewModel` type params: `<Event, State>` — 2 params only. Effect is `ViewSideEffect` internally.
- VM-less composables (no ViewModel) MUST wrap their own content with `TrackView` internally.
- `TrackView` MUST NEVER be called from NavGraph (outer). It's always inside: either via Container or inside the composable itself.
- Navigation MUST use `NavEffect` — never declare nav effects per-module.
- UI feedback MUST use `UiEffect` — never declare ShowError/ShowToast/ShowSnackbar per-module.
- `effectRouter` is ONLY for screen-specific effects (show sheet, apply filter, launch platform action).
- Module Contract effects contain ONLY screen-specific effects. No nav, no toast, no error.
- `setEffect` is VM-only — never call from composables, repositories, or use cases.
- UI dispatches events only: `dispatch(Event.X)`. Never navigate from composables directly.
- Flow: UI → Event → VM → setEffect/setUiState → Container → Navigator/UiEffect/effectRouter. One direction.
- Legacy screens using manual wiring still work — migrate incrementally.

## ScaffoldContainer (Host Environment)

Every Activity hosting Compose screens MUST wrap with `ScaffoldContainer`:

```kotlin
// DFM Activity:
ScaffoldContainer(navigator = localNavigator) {
    NavHost(navController, startDestination) { featureNavGraph() }
}

// MainScreen (provideEnvironment = false since AppNavGraph already provides WcmsProvider):
ScaffoldContainer(navigator = appNavigator, provideEnvironment = false, bottomBar = {...}, overlay = {...}) {
    NavHost(...) { ... }
}
```

Rules:
- DFM Activities MUST use `ScaffoldContainer` with `provideEnvironment = true` (default).
- DFM Activities MUST call `enableEdgeToEdge()` in `onCreate`.
- DFM Activities MUST provide a local `Navigator` implementation scoped to their NavHost.
- Cross-DFM deeplinks: use `getString(R.string.deeplink_scheme)` + implicit Intent. Never hardcode Activity class names.
- DFM manifest: declare intent-filters with `${deeplinkCustomScheme}` (custom) + `${deeplinkWebHost}` (HTTPS, `autoVerify="${deeplinkAutoVerify}"`).
