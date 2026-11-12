# 03 · Mobile app state (`rusuh-app`)

Repo: `PT-Meeting-Sampai-Malam-Konsultasi/rusuh-app` (GitHub), checkout `~/Projects/Rusuh-booking-ticket-app/rusuh-app`.
Checked 2026-11-09.

## Stack (from `gradle/libs.versions.toml`)

Kotlin 2.0.21 · Compose Multiplatform 1.7.0 · Ktor 3.0.1 · Koin 4.0.0 · kotlinx-serialization 1.7.3 ·
coroutines 1.9.0 · AGP 8.5.2 · androidx navigation 2.8.0-alpha10 (JetBrains) · detekt 1.23.7 · ktlint plugin 12.1.2 ·
kover 0.8.3. JVM toolchain/target **17**. Modules: `shared` (commonMain/androidMain/iosMain/commonTest),
`androidApp`, `iosApp/Rusuh.xcodeproj` (Swift host). Fastlane + Gemfile for iOS unsigned xcarchive.

## Git state (local)

- Local `main` is **behind origin/main by 2** (I ran `git fetch`; nothing else changed): `6578164` #53 pin jvmTarget 17,
  `5c14995` #54 i18n string resources EN+ID (adds `composeResources/values{,-id}/strings.xml`, migrates screens/components).
  **Pull before working** (`git pull --ff-only`) — but first deal with the uncommitted edits below.
- 3 **uncommitted** edits on `main`: `androidApp/build.gradle.kts`, `shared/build.gradle.kts` (add `jvmToolchain(17)`) and
  `build.gradle.kts` (only the trailing newline). These duplicate what #53 already merged (#53 touches `androidApp/build.gradle.kts`
  with +12 lines) → probably safe to discard, but they are the user's, so ask before discarding.
- `rusuh-app-m2`, `rusuh-app-m4` are extra **worktrees** of the same repo (old EPIC-00 M2 CI/CD, M4 Koin branches; remote
  branch of m2 is gone); another worktree at `/private/tmp/rusuh-m7`. All stale; candidates for cleanup (ask first).
- No open PRs. Last 10 PRs all merged on 2026-11-06 (#22–#54).

## Source layout (`shared/src/commonMain/kotlin/com/rusuh/app/`)

```
App.kt
data/network/        ApiClient.kt (baseUrl + Ktor client, logging), HttpClientFactory (expect/actual), NetworkResult.kt
di/                  AppModule, Modules (sharedModules list), KoinInit, PlatformModule (expect/actual)
mvi/                 MviViewModel<S, I, E>, MviState, MviIntent, MviEffect
navigation/          Screen.kt (string routes), NavGraph.kt, RusuhTab → route mapping
feature/home/        HomeContract, HomeViewModel, HomeRepository (+ StubHomeRepository → emptyList), HomeScreen, HomeEventItemMapper
feature/waitingroom/ WaitingRoomScreen (+Previews)   — UI only
feature/detail/      DetailScreen                    — UI only
feature/queues/      MyQueuesScreen, MyQueuesPlaceholder — UI only
ui/components/       EventCard, EventStatus, StatusBadge, QueuePositionCard, QueueListItem, SeatCategoryCard,
                     RusuhBottomBar, RusuhTab, ErrorState, LoadingState
ui/theme/            RusuhColors, FontFamily (Bricolage Grotesque + Space Grotesk in composeResources/font), Shapes, Spacing, TextStyles, Theme
```

Tests in `commonTest` for mapper/viewmodel/screens/components/tab mapping.

**Built:** design tokens, brand fonts, MVI base, nav + bottom bar (Event, Antrian, Tiket, Akun), Home MVI (stub repo),
UI-only Waiting Room / Detail / My Queues screens, i18n resources (after #54), CI.
**Not built:** Login/OTP, Account, Tickets tab, E-ticket, Payment (Snap handoff/App Link), Pre-queue/Paused/Missed/Sold-out
waiting states?, Dukcapil validation UI, device registration, secure storage, attestation, all real API wiring.
`Tiket` and `Akun` tabs are stub destinations (`Screen.Tickets`, `Screen.Account`).

## Conventions that apply to this code

- **Decision (user, 2026-11-09): KMP is final, and the reference project's steering in `workspace-local/kmp-docs/steering/*`
  is our inspiration — follow its patterns.** Today's code differs: `MviViewModel<S, I, E>` (3 type params, own `viewModelScope`,
  `Channel` effects), flat `feature/<name>/` folders, `Mvi*` names, Compose-resources strings, `Rusuh*` theme tokens,
  string-route `Screen` + `NavGraph`. Steering patterns to adopt for new code (and migrate old code incrementally):
  - `MVIViewModel<Event, State>` + `ViewState/ViewEvent/ViewSideEffect`, `ScreenStatus` enum (Loading/Success/Empty/Error), `@Immutable` state.
  - `ScreenContainer` / `SectionContainer` / `SheetContainer` / `DialogContainer` wiring; shared `NavEffect` and `UiEffect`
    (no per-module nav/toast effects); VMs call **UseCases only** (`CoreUseCase<P,R>.call(params)`), never repositories/ApiService.
  - Module layout `app/ navigation/ data/(remote,dto,mapper,repository) domain/(model,repository,usecase) di/`, one folder per screen
    with `Contract/ViewModel/Screen`; naming suffixes `Screen/Sheet/Dialog/Section/View`, atoms never wrap VM, no hard-coded strings.
  - Workflow: `./gradlew --no-daemon`, never `--no-verify`, human approval for destructive actions.
  - **Not applicable** (specific to the reference project): `wcms()`/WCMS, `DX.Color`/<reference design system>, Telegram notifications, reference/legacy emulator
    parity, WDIO `resId` rules, DFM Activities.
  The steering also needs a Rusuh-specific mapping when we adopt it (e.g. `wcms()` → Compose `stringResource`, `DX.Color` → `RusuhColors`);
  agree this mapping and a migration slice (EPIC-00-M5b) before refactoring existing screens.
- Slice rule used on GitHub: **M1** component UI, **M2** screen UI (placeholder data, no state/API), **M3** MVI state on a
  stub repository seam, **M4** API integration (replace stub). Seams are repository interfaces with stubs.
- Parent issues come from `rusuh-documentation` GitHub issues (FR-xx, read-only); `rusuh-app` re-slices each into M1–M4.
  Issue bodies cite hi-fi in `analysis/hifi/*.html` and now Figma.
- Workflow rules (from the user and kmp-docs, applicable ones): never `--no-verify`; `./gradlew --no-daemon`;
  ask before destructive git/file operations; commit/PR only when asked.
- Branch naming seen: `feat/fr-04-m1-m2-waiting-room-ui`, `fix/...`; PR titles like `FR-05-M1+M2: Detail Ticket Page UI (#40)`.

## CI (`.github/workflows`)

`android-build.yml`, `ios-build.yml`, `lint.yml` (detekt + ktlint), `sonar.yml`, `unit-test.yml`
(`./gradlew :shared:allTests koverXmlReport` and `:androidApp:testDebugUnitTest`, JDK 17). Detekt config in `config/detekt/detekt.yml`.

## Backend status

**Backend is not ready** (user, 2026-11-09). Leave all M4 / API-integration issues open; build UI + MVI on stub seams.
OpenAPI is a 0.3.0-draft contract and some M4 issues are explicitly parked until it is frozen.
