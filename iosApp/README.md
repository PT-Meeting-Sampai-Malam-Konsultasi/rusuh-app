# iosApp

iOS host app for the Rusuh Booking Tiket Compose Multiplatform UI. `iosApp/iOSApp.swift` starts Koin and embeds
the shared UI via `MainViewControllerKt.MainViewController()`.

## Run

Open `Rusuh.xcodeproj`, pick the `Rusuh` scheme and a simulator or device, and run. The "Build KMP Shared Framework"
script phase calls `./gradlew :shared:embedAndSignAppleFrameworkForXcode`, which builds the `shared` framework for
the selected SDK (simulator or device) and copies the Compose resources (fonts, strings) into the app bundle.
Command line:

```
xcodebuild -project iosApp/Rusuh.xcodeproj -scheme Rusuh -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16' build
```

## Backend

Debug builds talk to the live API by default; release builds always do. Scheme environment variables
(Edit Scheme > Run > Arguments) change that for debug builds only:

| Variable | Effect |
|---|---|
| `MOCK_API=true` | Use the local Mockoon server at `http://localhost:3000` |
| `BACKEND_MODE=Stub` | Offline demo data, no network |

`Info.plist` needs `CADisableMinimumFrameDurationOnPhone` (Compose refuses to start without it). `Info-Debug.plist`
is the same file plus cleartext HTTP to `localhost` for the mock; keep the two in sync.
