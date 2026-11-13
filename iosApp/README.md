# iosApp

iOS host app for the Rusuh Booking Tiket Compose Multiplatform UI. `iosApp/iOSApp.swift` starts Koin and embeds
the shared UI via `MainViewControllerKt.MainViewController()`.

## Schemes

Two schemes match the Android product flavors:

| Scheme | Display name | Environment | Debug options |
|---|---|---|---|
| `Rusuh Staging` | Rusuh Staging | Staging | Yes (debug builds) |
| `Rusuh Production` | Rusuh | Production | No |

Both share the same bundle identifier (`com.rusuh.app`). The display name is the only visible difference.

## Build configurations

Each scheme uses flavor-specific build configurations:

| Configuration | Use | Info.plist |
|---|---|---|
| `Debug-Staging` | Staging debug builds | Info-Debug.plist |
| `Release-Staging` | Staging release/archive | Info.plist |
| `Debug-Production` | Production debug builds | Info-Debug.plist |
| `Release-Production` | Production release/archive | Info.plist |

The `KOTLIN_FRAMEWORK_BUILD_TYPE` build setting maps the configuration to the KMP framework variant (Debug/Release).

## Run

Open `Rusuh.xcodeproj`, pick the `Rusuh Staging` or `Rusuh Production` scheme and a simulator or device, and run.
The "Build KMP Shared Framework" script phase calls `./gradlew :shared:embedAndSignAppleFrameworkForXcode`, which
builds the `shared` framework for the selected SDK (simulator or device) and copies the Compose resources (fonts,
strings) into the app bundle.

Command line:

```bash
# Staging (debug)
xcodebuild -project iosApp/Rusuh.xcodeproj -scheme "Rusuh Staging" -configuration Debug-Staging \
  -destination 'platform=iOS Simulator,name=iPhone 16' build

# Production (debug)
xcodebuild -project iosApp/Rusuh.xcodeproj -scheme "Rusuh Production" -configuration Debug-Production \
  -destination 'platform=iOS Simulator,name=iPhone 16' build
```

## Backend switches

The staging scheme in debug builds talks to the live staging API by default. Scheme environment variables
(Edit Scheme > Run > Arguments) change that:

| Variable | Effect |
|---|---|
| `MOCK_API=true` | Use the local Mockoon server at `http://localhost:3000` |
| `BACKEND_MODE=Stub` | Offline demo data, no network |

The production scheme **ignores** these variables, even in debug builds. Release builds (any scheme) always use
the production API.

## Info.plist

Both plists expose the environment settings via build variables:

| Key | Source | Values |
|---|---|---|
| `RusuhEnvironment` | `$(RUSUH_ENVIRONMENT)` | `Staging` or `Production` |
| `RusuhAllowDebugOptions` | `$(RUSUH_ALLOW_DEBUG_OPTIONS)` | `YES` or `NO` |
| `CFBundleDisplayName` | `$(RUSUH_APP_DISPLAY_NAME)` | `Rusuh Staging` or `Rusuh` |

`Info.plist` needs `CADisableMinimumFrameDurationOnPhone` (Compose refuses to start without it). `Info-Debug.plist`
is the same file plus cleartext HTTP to `localhost` for the mock; keep the two in sync.
