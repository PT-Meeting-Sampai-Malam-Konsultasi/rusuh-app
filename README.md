# Rusuh Booking Tiket

A Kotlin Multiplatform + Compose Multiplatform event booking app with a waiting room queue system. Runs on Android and iOS from a shared codebase.

## Modules

```
rusuh-app/
├── shared/        # KMP module: UI (Compose Multiplatform), domain, data, DI
├── androidApp/    # Android host app
└── iosApp/        # iOS host app (Xcode project)
```

The `shared` module contains:
- **UI**: Compose screens, navigation, string resources (EN/ID)
- **Domain**: Use cases, models, `DomainResult`
- **Data**: Repositories, `ApiService` classes, DTOs, mappers, `ApiClient`
- **DI**: Koin modules

ViewModels follow the MVI pattern: State, Effect, Event. Repositories return `DomainResult<T>` (Success, Error, Empty, NetworkError, TechnicalError).

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 17 | Required by Gradle and Android Gradle Plugin |
| Android Studio | Latest stable | Includes Android SDK; set `ANDROID_HOME` |
| Android SDK | API 24–36 | minSdk 24, targetSdk 34, compileSdk 36 |
| Xcode | 16.x | For iOS builds |
| Ruby + Bundler | 3.x | For Fastlane (iOS CI/distribution) |

## First-time setup

1. Clone the repository:
   ```sh
   git clone <repo-url>
   cd rusuh-app
   ```

2. Create `local.properties` in the repo root with your Android SDK path:
   ```properties
   sdk.dir=/Users/<you>/Library/Android/sdk
   ```
   On macOS with Android Studio defaults: `sdk.dir=/Users/$USER/Library/Android/sdk`

3. (Optional) Add `androidApp/google-services.json` for Firebase Analytics:
   - Download from Firebase Console > Project settings > Your apps > Android app
   - Place at `androidApp/google-services.json`
   - **Without it**: the build succeeds, but Firebase Analytics is disabled (no-op)
   - **With it**: analytics events are collected (consent required in-app)

4. (Optional) Install Fastlane dependencies:
   ```sh
   bundle install
   ```

## Running Android

Open the project in Android Studio, select a flavor and build variant, then run.

### Flavors

| Flavor | Launcher name | API | Debug options |
|--------|---------------|-----|---------------|
| `staging` | Rusuh Staging | Staging API | Yes (debug builds) |
| `production` | Rusuh | Production API | No |

### Build variants

```sh
# Staging debug (live staging API, default)
./gradlew :androidApp:assembleStagingDebug

# Staging debug with local mock server
./gradlew :androidApp:assembleStagingDebug -PmockApi=true

# Staging debug with offline stub data
./gradlew :androidApp:assembleStagingDebug -PbackendMode=Stub

# Production debug (production API, no debug options)
./gradlew :androidApp:assembleProductionDebug
```

The `-PmockApi` and `-PbackendMode` flags only affect **staging debug** builds. Production builds and all release builds ignore them.

## Running iOS

Open `iosApp/Rusuh.xcodeproj` in Xcode, select a scheme and simulator/device, then run.

### Schemes

| Scheme | Display name | Environment | Debug options |
|--------|--------------|-------------|---------------|
| Rusuh Staging | Rusuh Staging | Staging | Yes (debug builds) |
| Rusuh Production | Rusuh | Production | No |

### Backend switches (staging debug only)

Edit Scheme > Run > Arguments > Environment Variables:

| Variable | Effect |
|----------|--------|
| `MOCK_API=true` | Use local Mockoon server at `http://localhost:3000` |
| `BACKEND_MODE=Stub` | Offline demo data, no network |

Command-line build:
```sh
xcodebuild -project iosApp/Rusuh.xcodeproj -scheme "Rusuh Staging" -configuration Debug-Staging \
  -destination 'platform=iOS Simulator,name=iPhone 16' build
```

See [iosApp/README.md](iosApp/README.md) for build configurations and Info.plist details.

## Local mock server

The app can connect to a local [Mockoon](https://mockoon.com/) server for development. The mock server configuration lives outside the repository (workspace-local).

- **Android emulator**: `http://10.0.2.2:3000` (emulator routes this to host machine)
- **iOS simulator**: `http://localhost:3000`
- **Physical Android device**: use `adb reverse tcp:3000 tcp:3000` or add the machine's LAN IP to `network_security_config.xml`

The mock server is optional. The live staging API (`https://api.rusuh-tiket.web.id/v1`) is the default.

## Tests and checks

### Linting

```sh
./gradlew ktlintCheck
./gradlew detekt
```

### Unit tests

```sh
# Shared module tests (Robolectric, no emulator needed)
./gradlew :shared:testDebugUnitTest

# All shared tests (commonTest + platform tests)
./gradlew :shared:allTests

# Android app tests (staging)
./gradlew :androidApp:testStagingDebugUnitTest
```

### iOS compile check

```sh
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64
```

### Connected tests (optional, requires emulator/device)

```sh
./gradlew :androidApp:connectedStagingDebugAndroidTest -PbackendMode=Stub
```

## CI and Firebase distribution

GitHub Actions workflows:
- **lint.yml**: ktlintCheck, detekt, plist sync check on PRs
- **unit-test.yml**: shared and Android unit tests, coverage report
- **android-build.yml**: build both flavors, distribute staging debug to Firebase App Distribution on push to main
- **ios-build.yml**: build staging archive and production simulator build

See [docs/firebase-distribution.md](docs/firebase-distribution.md) for setup and one-time configuration.

## Documentation

The `docs/` directory contains:
- [data-layer.md](docs/data-layer.md) – API client, result types, repository pattern
- [firebase-distribution.md](docs/firebase-distribution.md) – CI distribution setup
- [testing.md](docs/testing.md) – test patterns and Robolectric setup
- [ios-flavors.md](docs/ios-flavors.md) – iOS scheme/configuration details
- [language-switch.md](docs/language-switch.md) – EN/ID locale handling
- [claude-notes/](docs/claude-notes/) – working notes from development

## Contributing

1. **Issue first**: open or reference an issue before starting work
2. **Branch**: create a feature branch from `main`
3. **PR title**: `CODE (#issue): Description` (e.g., `FR-01-M1 (#42): Add event list screen`)
4. **Commits**: every commit message must include the issue number
5. **No secrets**: never commit API keys, tokens, or credentials
6. **Synthetic data only**: use fake data in tests and screenshots (public repo)

## View Android analytics in DebugView

Install a staging debug build on a device or emulator, then run:

```sh
adb shell setprop log.tag.FA VERBOSE
adb shell setprop debug.firebase.analytics.app com.rusuh.app.android
```

Grant analytics consent in the app and watch `adb logcat` for `Logging event`. In Firebase Console, open Analytics > DebugView and select the device. Setup for `google-services.json` and CI is in [Firebase distribution](docs/firebase-distribution.md).
