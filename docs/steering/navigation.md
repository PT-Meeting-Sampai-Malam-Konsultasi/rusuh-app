---
inclusion: always
---
# Navigation

## Navigator Interface

All navigation flows through `Navigator` in `:features:commons`:

```kotlin
interface Navigator {
    fun navigateBack()
    fun navigate(route: Any)
    fun navigateDeeplink(cta: String)
    fun navigateToLogin(pendingRoute: String? = null)
    fun showMsisdnChanger(config: MsisdnChangerConfig? = MsisdnChangerConfig())
    fun requestStatusBarChange(light: Boolean)
    fun logout()
}
```

Implementation (`AppNavigator`) lives in `:app` — handles dual NavHost routing internally.

## How Navigation Works with Containers

Navigation is automatic via `NavigableEffect` — see mvi-container.md. Containers inject Navigator and dispatch effects without manual wiring.

## Module-Owned Routes

```kotlin
// shop/navigation/ShopRoutes.kt
@Serializable data object ShopRoute : Route()
@Serializable data class PackageDetailRoute(val bid: String) : Route()
```

## TabHostedRoutes

Routes in the tab NavHost are listed in one place:
```kotlin
object TabHostedRoutes {
    fun isTabHosted(route: Any): Boolean = when (route) { ... }
}
```

When adding a new route to the tab NavHost, add it to `TabHostedRoutes`.

## Rules

- Adding a new screen **never requires editing AppNavGraph**.
- Only **data parameters** are allowed as NavGraph function args (`retryTrigger`, `paymentState`).
- Feature modules **never** import `:app`.
- Never inject `AppNavigator` directly — always use the `Navigator` interface.
- Auth-guarded routes: use `navigator.navigateToLogin(pendingRoute)`.

## Deeplink Payloads

A deeplink that must carry data past the navigation hop (e.g. "open See-All for keyword X on the Shop tab") uses `PendingDeeplinkStore` in `:features:commons` — never a feature-local `object` holder.

- The feature owns its payload types: `sealed interface ShopPayload : DeeplinkPayload` in `navigation/`. `:app` and `commons` never import them.
- Producer: `{Module}DeeplinkHandler` returns the payload; `CtaRouter` puts it in `Target.Tab(index, payload)`; the tab-switch site calls `publishPayload(store)`.
- Consumer: the destination ViewModel collects a feature `Observe{Module}PayloadUseCase` (`store.observe<T>()`) in `init` and maps it to an Event. Delivery is buffered until an observer exists and happens exactly once. No `LaunchedEffect` in the NavGraph.
- One deeplink is in flight at a time — a newer `publish` replaces an unconsumed payload. Call `store.clear()` when a payload must not outlive its session.
